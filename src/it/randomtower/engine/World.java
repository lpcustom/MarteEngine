package it.randomtower.engine;

import it.randomtower.engine.actors.StaticActor;
import it.randomtower.engine.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.newdawn.slick.Color;
import org.newdawn.slick.GameContainer;
import org.newdawn.slick.Graphics;
import org.newdawn.slick.Image;
import org.newdawn.slick.SlickException;
import org.newdawn.slick.geom.Rectangle;
import org.newdawn.slick.state.BasicGameState;
import org.newdawn.slick.state.StateBasedGame;
import org.newdawn.slick.tiled.TiledMap;
import org.newdawn.slick.util.Log;

//TODO addAll() muss intern add() aufrufen, um korrekt nach flags in die listen einzusortieren
public class World extends BasicGameState {

	public static final int BELOW = -1;
	public static final int GAME = 0;
	public static final int ABOVE = 1;
	
	/** the game container this world belongs to */
	public GameContainer container = null;

	/** unique id for every world **/
	public int id = 0;

	/** width of the world, useful for horizontal wrapping entitites */
	public int width = 0;
	/** height of the world, useful for vertical wrapping entities */
	public int height = 0;
	
	/** internal list for entities **/
	private List<Entity> entities = new ArrayList<Entity>();
	private List<Entity> removable = new ArrayList<Entity>();
	private List<Entity> addable = new ArrayList<Entity>();
	
	/** two lists to contain objects that are rendered before and after camera stuff is rendered */
	private List<Entity> belowCamera = new ArrayList<Entity>();
	private List<Entity> aboveCamera = new ArrayList<Entity>();

	/** current camera **/
	public Camera camera;
	
	public int renderedEntities;

	public World(int id) {
		this.id = id;
	}

	public World(int id, GameContainer container) {
		this.id = id;
		this.container = container;
	}

	public void init(GameContainer container, StateBasedGame game)
			throws SlickException {
		this.container = container;
		if (width == 0)
			width = container.getWidth();
		if (height == 0)
			height = container.getHeight();
		// this.clear();
	}

	@Override
	public void enter(GameContainer container, StateBasedGame game)
			throws SlickException {
		ME.world = this;
	}

	public void render(GameContainer container, StateBasedGame game, Graphics g)
			throws SlickException {

		renderedEntities = 0;
		// first render entities below camera
		for (Entity e:belowCamera) {
			if (!e.visible)
				continue;
			renderEntity(e, g, container);
		}
		// center to camera position
		if (camera != null)
			g.translate(-camera.cameraX, -camera.cameraY);

		// render entities
		for (Entity e : entities) {
			if (!e.visible)
				continue;	// next entity. this one stays invisible
			if (camera != null) {
				if (camera.contains(e)) {
					renderEntity(e, g, container);
				}
			} else {
				renderEntity(e, g, container);
			}
		}

		if (camera != null)
			g.translate(camera.cameraX, camera.cameraY);

		// finally render entities above camera
		for (Entity e:aboveCamera) {
			if (!e.visible)
				continue;
			renderEntity(e, g, container);
		}
		
		ME.render(container, game, g);
	}

	private void renderEntity(Entity e, Graphics g, GameContainer container) throws SlickException {
		renderedEntities++;
		if (ME.debugEnabled) {
			g.setColor(ME.borderColor);
			Rectangle hitBox = new Rectangle(e.x + e.hitboxOffsetX, e.y
					+ e.hitboxOffsetY, e.hitboxWidth, e.hitboxHeight);
			g.draw(hitBox);
			g.setColor(Color.white);
		}
		e.render(container, g);
	}
	
	public void update(GameContainer container, StateBasedGame game, int delta)
			throws SlickException {
		if (container == null)
			throw new SlickException("no container set");

		// store the current delta in ME for anyone who's interested in it.
		ME.delta = delta;

		// add new entities
		if (addable.size() > 0) {
			for (Entity entity : addable) {
				entities.add(entity);
				entity.addedToWorld();
			}
			addable.clear();
			Collections.sort(entities);
		}

		// update entities
		for (Entity e : belowCamera) {
			e.updateAlarms(delta);
			if (e.active)
				e.update(container, delta);
		}
		for (Entity e : entities) {
			e.updateAlarms(delta);
			if (e.active)
				e.update(container, delta);
			// check for wrapping or out of world entities
			e.checkWorldBoundaries();
		}
		for (Entity e : aboveCamera) {
			e.updateAlarms(delta);
			if (e.active)
				e.update(container, delta);
		}
		// remove signed entities
		for (Entity entity : removable) {
			entities.remove(entity);
			belowCamera.remove(entity);
			aboveCamera.remove(entity);
			entity.removedFromWorld();
		}

		// update camera
		if (camera != null) {
			camera.update(container, delta);
		}

		ME.update(container, game, delta);
	}

	@Override
	public int getID() {
		return id;
	}

	/**
	 * Add entity to world and sort entity in z order
	 * 
	 * @param e
	 *            entity to add
	 */
	public void add(Entity e, int ...flags) {
		e.setWorld(this);
		if (flags.length == 1) {
			switch(flags[0]) {
			case BELOW:
				belowCamera.add(e);
				break;
			case GAME:
				addable.add(e);
				break;
			case ABOVE:
				aboveCamera.add(e);
				break;
			}
		} else
			addable.add(e);
	}

	public void addAll(Collection<Entity> e, int ...flags) {
		for (Entity entity : e) {
			this.add(entity, flags);
		}
	}

	/**
	 * @return List of entities currently in this world
	 */
	public List<Entity> getEntities() {
		return entities;
	}

	/**
	 * 
	 * @param type
	 * @return number of entities of the given type in this world
	 */
	public int getNrOfEntities(String type) {
		if (entities.size() > 0) {
			int number = 0;
			for (Entity entity : entities) {
				if (entity.getType().contains(type))
					number++;
			}
			return number;
		}
		return 0;
	}
	
	
	public List<Entity> getEntities(String type) {
		if (entities.size() > 0) {
			List<Entity> res = new ArrayList<Entity>();
			for (Entity entity : entities) {
				if (entity.getType().contains(type))
					res.add(entity);
			}
			return res;
		}
		return null;
	}

	/**
	 * @param entity
	 *            to remove from game
	 * @return false if entity is already set to be remove
	 */
	public boolean remove(Entity entity) {
		if (!removable.contains(entity)) {
			return removable.add(entity);
		}
		return false;
	}

	/**
	 * @param name
	 * @return null if name is null or if no entity is found in game, entity
	 *         otherwise
	 */
	public Entity find(String name) {
		if (name == null)
			return null;
		for (Entity entity : entities) {
			if (entity.name != null && entity.name.equalsIgnoreCase(name)) {
				return entity;
			}
		}
		// also look in addable list
		for (Entity entity : addable) {
			if (entity.name != null && entity.name.equalsIgnoreCase(name)) {
				return entity;
			}
		}
		// and look in aboveCamera and belowCamera list
		for (Entity entity : aboveCamera) {
			if (entity.name != null && entity.name.equalsIgnoreCase(name)) {
				return entity;
			}
		}
		for (Entity entity : belowCamera) {
			if (entity.name != null && entity.name.equalsIgnoreCase(name)) {
				return entity;
			}
		}
		return null;
	}

	/**
	 * Remove all entities
	 */
	public void clear() {
		for (Entity entity : entities) {
			entity.removedFromWorld();
		}
		belowCamera.clear();
		aboveCamera.clear();
		entities.clear();
		addable.clear();
		removable.clear();
	}

	public void setCamera(Camera camera) {
		this.camera = camera;
		this.camera.setMyWorld(this);
	}

	public void setCameraOn(Entity entity) {
		if (camera == null) {
			this.setCamera(new Camera(this, entity, this.container.getWidth(),
				this.container.getHeight()));
		}
		this.camera.setFollow(entity);
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	/**
	 * Load entity from a tiled map into current World
	 * 
	 * @param map
	 */
	public void loadEntityFromMap(TiledMap map) {
		if (map == null) {
			Log.error("unable to load map information");
			return;
		}
		// try to find a layer with property type set to entity
		int layerIndex = -1;
		for (int l = 0; l < map.getLayerCount(); l++) {
			String value = map.getLayerProperty(l, "type", null);
			if (value != null && value.equalsIgnoreCase("entity")) {
				layerIndex = l;
				break;
			}
		}
		if (layerIndex != -1) {
			Log.debug("Entity layer found on map");
			for (int w = 0; w < map.getWidth(); w++) {
				for (int h = 0; h < map.getHeight(); h++) {
					Image img = map.getTileImage(w, h, layerIndex);
					if (img != null) {
						StaticActor te = new StaticActor(w * img.getWidth(), h
								* img.getHeight(), img.getWidth(),
								img.getHeight(), img);
						add(te);
					}
				}
			}
		}

	}

	public List<Entity> findEntityWithType(String type) {
		if (type == null) {
			Log.error("Parameter must be not null");
			return null;
		}
		List<Entity> result = new ArrayList<Entity>();
		for (Entity entity : entities) {
			if (entity.getType().contains(type)) {
				result.add(entity);
			}
		}
		return result;
	}

	/**
	 * @param x
	 * @param y
	 * @return true if an entity is already in position
	 */
	public boolean isEmpty(int x, int y, int depth) {
		Rectangle rect;
		for (Entity entity : entities) {
			rect = new Rectangle(entity.x, entity.y, entity.width,
					entity.height);
			if (entity.depth == depth && rect.contains(x, y)) {
				return false;
			}
		}
		return true;
	}

	public Entity find(int x, int y) {
		Rectangle rect;
		for (Entity entity : entities) {
			rect = new Rectangle(entity.x, entity.y, entity.width,
					entity.height);
			if (rect.contains(x, y)) {
				return entity;
			}
		}
		return null;
	}

}
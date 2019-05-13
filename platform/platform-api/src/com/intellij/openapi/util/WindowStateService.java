/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Sergey.Malenkov
 */
public abstract class WindowStateService {
  /**
   * @return an instance of the service for the application
   */
  public static WindowStateService getInstance() {
    return ServiceManager.getService(WindowStateService.class);
  }

  /**
   * @param project the project to use by the service
   * @return an instance of the service for the specified project
   */
  public static WindowStateService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, WindowStateService.class);
  }

  /**
   * Loads a state of the specified component by the specified key.
   *
   * @param key       an unique string key
   * @param component a component which state should be changed
   * @return {@code true} if a state is loaded successfully, {@code false} otherwise
   */
  public final boolean loadState(@NotNull String key, @NotNull Component component) {
    return loadStateFor(null, key, component);
  }

  /**
   * Loads a state of the specified component by the given screen and the specified key.
   * A screen can be specified by {@link Project}, {@link Window}, or {@link GraphicsDevice}.
   *
   * @param object    an object that specifies a screen to which a component state belongs
   * @param key       an unique string key
   * @param component a component which state should be changed
   * @return {@code true} if a state is loaded successfully, {@code false} otherwise
   */
  public abstract boolean loadStateFor(Object object, @NotNull String key, @NotNull Component component);

  /**
   * Stores the specified location that corresponds to the specified key.
   * If it is {@code null} the stored location will be removed.
   *
   * @param key       an unique string key
   * @param component a component which state should be saved
   */
  public final void saveState(@NotNull String key, @NotNull Component component) {
    saveStateFor(null, key, component);
  }

  /**
   * Stores the specified location that corresponds to the given screen and the specified key.
   * If it is {@code null} the stored location will be removed.
   * A screen can be specified by {@link Project}, {@link Window}, or {@link GraphicsDevice}.
   * Do not use a screen which is calculated from the specified component.
   *
   * @param object    an object that specifies a screen to which a component state belongs
   * @param key       an unique string key
   * @param component a component which state should be saved
   */
  public abstract void saveStateFor(Object object, @NotNull String key, @NotNull Component component);

  /**
   * Returns a location that corresponds to the specified key or {@code null}
   * if a location does not exist or it is outside of visible area.
   *
   * @param key an unique string key
   * @return a corresponding location
   */
  public final Point getLocation(@NotNull String key) {
    return getLocationFor(null, key);
  }

  /**
   * Returns a location that corresponds to the given screen and the specified key or {@code null}
   * if a location does not exist or it is outside of visible area.
   * A screen can be specified by {@link Project}, {@link Window}, or {@link GraphicsDevice}.
   *
   * @param object an object that specifies a screen to which a location belongs
   * @param key    an unique string key
   * @return a corresponding location
   */
  public abstract Point getLocationFor(Object object, @NotNull String key);

  /**
   * Stores the specified location that corresponds to the specified key.
   * If it is {@code null} the stored location will be removed.
   *
   * @param key an unique string key
   */
  public final void putLocation(@NotNull String key, Point location) {
    putLocationFor(null, key, location);
  }

  /**
   * Stores the specified location that corresponds to the given screen and the specified key.
   * If it is {@code null} the stored location will be removed.
   * A screen can be specified by {@link Project}, {@link Window}, or {@link GraphicsDevice}.
   * Do not use a screen which is calculated from the specified location.
   *
   * @param object an object that specifies a screen to which a location belongs
   * @param key    an unique string key
   */
  public abstract void putLocationFor(Object object, @NotNull String key, Point location);

  /**
   * Returns a size that corresponds to the specified key or {@code null}
   * if a size does not exist.
   *
   * @param key an unique string key
   * @return a corresponding size
   */
  public final Dimension getSize(@NotNull String key) {
    return getSizeFor(null, key);
  }

  /**
   * Returns a size that corresponds to the given screen and the specified key or {@code null}
   * if a size does not exist.
   * A screen can be specified by {@link Project}, {@link Window}, or {@link GraphicsDevice}.
   *
   * @param object an object that specifies a screen to which a size belongs
   * @param key    an unique string key
   * @return a corresponding size
   */
  public abstract Dimension getSizeFor(Object object, @NotNull String key);

  /**
   * Stores the specified size that corresponds to the specified key.
   * If it is {@code null} the stored size will be removed.
   *
   * @param key an unique string key
   */
  public final void putSize(@NotNull String key, Dimension size) {
    putSizeFor(null, key, size);
  }

  /**
   * Stores the specified size that corresponds to the given screen and the specified key.
   * If it is {@code null} the stored size will be removed.
   * A screen can be specified by {@link Project}, {@link Window}, or {@link GraphicsDevice}.
   * Do not use a screen which is calculated from the specified size.
   *
   * @param object an object that specifies a screen to which a size belongs
   * @param key    an unique string key
   */
  public abstract void putSizeFor(Object object, @NotNull String key, Dimension size);

  /**
   * Returns a bounds that corresponds to the specified key or {@code null}
   * if a bounds does not exist or it is outside of visible area.
   *
   * @param key an unique string key
   * @return a corresponding bounds
   */
  public final Rectangle getBounds(@NotNull String key) {
    return getBoundsFor(null, key);
  }

  /**
   * Returns a bounds that corresponds to the given screen and the specified key or {@code null}
   * if a bounds does not exist or it is outside of visible area.
   * A screen can be specified by {@link Project}, {@link Window}, or {@link GraphicsDevice}.
   *
   * @param object an object that specifies a screen to which a bounds belongs
   * @param key    an unique string key
   * @return a corresponding bounds
   */
  public abstract Rectangle getBoundsFor(Object object, @NotNull String key);

  /**
   * Stores the specified bounds that corresponds to the specified key.
   * If it is {@code null} the stored bounds will be removed.
   *
   * @param key an unique string key
   */
  public final void putBounds(@NotNull String key, Rectangle bounds) {
    putBoundsFor(null, key, bounds);
  }

  /**
   * Stores the specified bounds that corresponds to the given screen and the specified key.
   * If it is {@code null} the stored bounds will be removed.
   * A screen can be specified by {@link Project}, {@link Window}, or {@link GraphicsDevice}.
   * Do not use a screen which is calculated from the specified bounds.
   *
   * @param object an object that specifies a screen to which a bounds belongs
   * @param key    an unique string key
   */
  public abstract void putBoundsFor(Object object, @NotNull String key, Rectangle bounds);
}

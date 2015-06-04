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
    return loadStateOn(null, key, component);
  }

  /**
   * Loads a state of the specified component by the given screen and the specified key.
   *
   * @param screen    a screen to which a location belongs
   * @param key       an unique string key
   * @param component a component which state should be changed
   * @return {@code true} if a state is loaded successfully, {@code false} otherwise
   */
  public abstract boolean loadStateOn(GraphicsDevice screen, @NotNull String key, @NotNull Component component);

  /**
   * Stores the specified location that corresponds to the specified key.
   * If it is {@code null} the stored location will be removed.
   *
   * @param key       an unique string key
   * @param component a component which state should be saved
   */
  public final void saveState(@NotNull String key, @NotNull Component component) {
    saveStateOn(null, key, component);
  }

  /**
   * Stores the specified location that corresponds to the given screen and the specified key.
   * If it is {@code null} the stored location will be removed.
   * Do not use a screen which is calculated from the specified component.
   *
   * @param screen    a screen to which a location belongs
   * @param key       an unique string key
   * @param component a component which state should be saved
   */
  public abstract void saveStateOn(GraphicsDevice screen, @NotNull String key, @NotNull Component component);

  /**
   * Returns a location that corresponds to the specified key or {@code null}
   * if a location does not exist or it is outside of visible area.
   *
   * @param key an unique string key
   * @return a corresponding location
   */
  public final Point getLocation(@NotNull String key) {
    return getLocationOn(null, key);
  }

  /**
   * Returns a location that corresponds to the given screen and the specified key or {@code null}
   * if a location does not exist or it is outside of visible area.
   *
   * @param screen a screen to which a location belongs
   * @param key    an unique string key
   * @return a corresponding location
   */
  public abstract Point getLocationOn(GraphicsDevice screen, @NotNull String key);

  /**
   * Stores the specified location that corresponds to the specified key.
   * If it is {@code null} the stored location will be removed.
   *
   * @param key an unique string key
   */
  public final void putLocation(@NotNull String key, Point location) {
    putLocationOn(null, key, location);
  }

  /**
   * Stores the specified location that corresponds to the given screen and the specified key.
   * If it is {@code null} the stored location will be removed.
   * Do not use a screen which is calculated from the specified location.
   *
   * @param screen a screen to which a location belongs
   * @param key    an unique string key
   */
  public abstract void putLocationOn(GraphicsDevice screen, @NotNull String key, Point location);

  /**
   * Returns a size that corresponds to the specified key or {@code null}
   * if a size does not exist.
   *
   * @param key an unique string key
   * @return a corresponding size
   */
  public final Dimension getSize(@NotNull String key) {
    return getSizeOn(null, key);
  }

  /**
   * Returns a size that corresponds to the given screen and the specified key or {@code null}
   * if a size does not exist.
   *
   * @param screen a screen to which a size belongs
   * @param key    an unique string key
   * @return a corresponding size
   */
  public abstract Dimension getSizeOn(GraphicsDevice screen, @NotNull String key);

  /**
   * Stores the specified size that corresponds to the specified key.
   * If it is {@code null} the stored size will be removed.
   *
   * @param key an unique string key
   */
  public final void putSize(@NotNull String key, Dimension size) {
    putSizeOn(null, key, size);
  }

  /**
   * Stores the specified size that corresponds to the given screen and the specified key.
   * If it is {@code null} the stored size will be removed.
   * Do not use a screen which is calculated from the specified size.
   *
   * @param screen a screen to which a size belongs
   * @param key    an unique string key
   */
  public abstract void putSizeOn(GraphicsDevice screen, @NotNull String key, Dimension size);

  /**
   * Returns a bounds that corresponds to the specified key or {@code null}
   * if a bounds does not exist or it is outside of visible area.
   *
   * @param key an unique string key
   * @return a corresponding bounds
   */
  public final Rectangle getBounds(@NotNull String key) {
    return getBoundsOn(null, key);
  }

  /**
   * Returns a bounds that corresponds to the given screen and the specified key or {@code null}
   * if a bounds does not exist or it is outside of visible area.
   *
   * @param screen a screen to which a bounds belongs
   * @param key    an unique string key
   * @return a corresponding bounds
   */
  public abstract Rectangle getBoundsOn(GraphicsDevice screen, @NotNull String key);

  /**
   * Stores the specified bounds that corresponds to the specified key.
   * If it is {@code null} the stored bounds will be removed.
   *
   * @param key an unique string key
   */
  public final void putBounds(@NotNull String key, Rectangle bounds) {
    putBoundsOn(null, key, bounds);
  }

  /**
   * Stores the specified bounds that corresponds to the given screen and the specified key.
   * If it is {@code null} the stored bounds will be removed.
   * Do not use a screen which is calculated from the specified bounds.
   *
   * @param screen a screen to which a bounds belongs
   * @param key    an unique string key
   */
  public abstract void putBoundsOn(GraphicsDevice screen, @NotNull String key, Rectangle bounds);
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public abstract class WindowStateService {
  private final Project project;

  protected WindowStateService(@Nullable Project project) {
    this.project = project;
  }


  /**
   * @return an instance of the service for the application
   */
  public static WindowStateService getInstance() {
    return ApplicationManager.getApplication().getService(WindowStateService.class);
  }

  /**
   * @param project the project to use by the service
   * @return an instance of the service for the specified project
   */
  public static WindowStateService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, WindowStateService.class);
  }


  /**
   * Returns a window state by the specified key.
   * Also it adds a listener to save a modified state automatically.
   *
   * @param key    an unique string key
   * @param window a window state which should be watched for
   * @return a corresponding window state
   */
  public final WindowState getState(@NotNull String key, @NotNull Window window) {
    return getStateFor(project, key, window);
  }

  /**
   * Returns a window state by the given project and the specified key.
   * Also it adds a listener to save a modified state automatically.
   *
   * @param project an project that specifies a main screen
   * @param key     an unique string key
   * @param window  a window state which should be watched for
   * @return a corresponding window state
   */
  public abstract WindowState getStateFor(@Nullable Project project, @NotNull String key, @NotNull Window window);


  /**
   * Returns a location that corresponds to the specified key or {@code null}
   * if a location does not exist or it is outside of visible area.
   *
   * @param key an unique string key
   * @return a corresponding location
   */
  public final Point getLocation(@NotNull String key) {
    return getLocationFor(project, key);
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
    putLocationFor(project, key, location);
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
    return getSizeFor(project, key);
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
    putSizeFor(project, key, size);
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
    return getBoundsFor(project, key);
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
    putBoundsFor(project, key, bounds);
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

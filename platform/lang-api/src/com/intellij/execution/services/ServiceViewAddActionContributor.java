// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Implemented by direct children of the {@code ServiceView.AddService} action group to declare which
 * {@link ServiceViewContributor} they belong to.
 * <p>
 * When the {@code ServiceView.AddService} group is expanded inside a tool window, children that implement this
 * interface are kept only if their contributor's tool window id matches the current one. Outside of a tool window
 * (for example, in {@code ToolsMenu.Services} or action search) all such children remain visible.
 * <p>
 * Children that do not implement this interface are not filtered.
 */
@ApiStatus.Experimental
public interface ServiceViewAddActionContributor {
  /**
   * The {@link ServiceViewContributor} class whose tool window owns this action.
   */
  @NotNull Class<?> getContributorClass();
}

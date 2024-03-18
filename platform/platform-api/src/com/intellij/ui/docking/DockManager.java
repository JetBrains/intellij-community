// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.docking;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Set;
import java.util.function.Predicate;

public abstract class DockManager {

  public abstract void register(@NotNull DockContainer container, @NotNull Disposable parentDisposable);

  public abstract void register(@NotNull String id, @NotNull DockContainerFactory factory, @NotNull Disposable parentDisposable);

  public static DockManager getInstance(@NotNull Project project) {
    return project.getService(DockManager.class);
  }

  public abstract DragSession createDragSession(MouseEvent mouseEvent, @NotNull DockableContent<?> content);

  public abstract @NotNull Set<@NotNull DockContainer> getContainers();

  public abstract IdeFrame getIdeFrame(@NotNull DockContainer container);

  public abstract String getDimensionKeyForFocus(@NotNull String key);

  public abstract @Nullable DockContainer getContainerFor(@Nullable Component c, @NotNull Predicate<? super DockContainer> filter);
}

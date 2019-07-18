// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.docking;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Set;

public abstract class DockManager {
  public abstract void register(DockContainer container);

  public abstract void register(String id, DockContainerFactory factory);

  public static DockManager getInstance(Project project) {
    return ServiceManager.getService(project, DockManager.class);
  }

  public abstract DragSession createDragSession(MouseEvent mouseEvent, @NotNull DockableContent content);

  public abstract Set<DockContainer> getContainers();

  public abstract IdeFrame getIdeFrame(DockContainer container);

  public abstract String getDimensionKeyForFocus(@NotNull String key);

  @Nullable
  public abstract DockContainer getContainerFor(Component c);
}

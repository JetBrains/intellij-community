// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.docking;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;

@ApiStatus.Internal
public interface DragSession {
  @NotNull
  DockContainer.ContentResponse getResponse(MouseEvent e);

  void process(MouseEvent e);

  void cancel();
}

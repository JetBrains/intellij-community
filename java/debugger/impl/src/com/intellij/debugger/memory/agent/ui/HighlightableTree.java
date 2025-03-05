// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent.ui;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class HighlightableTree extends XDebuggerTree {
  private final Map<TreePath, Color> myPathToBackgroundColor;

  public HighlightableTree(@NotNull Project project,
                           @NotNull XDebuggerEditorsProvider editorsProvider,
                           @Nullable XSourcePosition sourcePosition,
                           @NotNull String popupActionGroupId,
                           @Nullable XValueMarkers<?, ?> valueMarkers) {
    super(project, editorsProvider, sourcePosition, popupActionGroupId, valueMarkers);
    myPathToBackgroundColor = new HashMap<>();
  }

  @Override
  public @Nullable Color getFileColorForPath(@NotNull TreePath path) {
    Color color = myPathToBackgroundColor.get(path);
    return color != null ? color : super.getFileColorForPath(path);
  }

  @Override
  public void dispose() {
    super.dispose();
    clearColoredPaths();
  }

  public void addColoredPath(TreePath path, Color color) {
    myPathToBackgroundColor.put(path, color);
  }

  public void removeColoredPath(TreePath path) {
    myPathToBackgroundColor.remove(path);
  }

  public void removeColoredPaths(TreePath[] paths) {
    for (TreePath path : paths) {
      myPathToBackgroundColor.remove(path);
    }
  }

  public void clearColoredPaths() {
    myPathToBackgroundColor.clear();
  }

  @Override
  public boolean isFileColorsEnabled() {
    return !myPathToBackgroundColor.isEmpty();
  }
}

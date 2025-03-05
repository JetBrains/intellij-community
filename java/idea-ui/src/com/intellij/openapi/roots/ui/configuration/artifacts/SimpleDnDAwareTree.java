// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.ide.dnd.DnDAware;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class SimpleDnDAwareTree extends SimpleTree implements DnDAware {
  @Override
  public void processMouseEvent(MouseEvent e) {
    if (getToolTipText() == null && e.getID() == MouseEvent.MOUSE_ENTERED) return;
    super.processMouseEvent(e);
  }

  @Override
  public boolean isOverSelection(Point point) {
    return TreeUtil.isOverSelection(this, point);
  }

  @Override
  public void dropSelectionButUnderPoint(Point point) {
    TreeUtil.dropSelectionButUnderPoint(this, point);
  }

  @Override
  public @NotNull JComponent getComponent() {
    return this;
  }
}

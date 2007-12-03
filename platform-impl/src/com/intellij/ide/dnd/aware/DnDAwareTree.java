package com.intellij.ide.dnd.aware;

import com.intellij.util.ui.Tree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ide.dnd.DnDEnabler;
import com.intellij.ide.dnd.DnDAware;

import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.*;
import java.awt.event.MouseEvent;
import java.awt.*;

import org.jetbrains.annotations.NotNull;

public class DnDAwareTree extends Tree implements DnDAware {
  private DnDEnabler myDnDEnabler;

  public DnDAwareTree() {
  }

  public DnDAwareTree(final TreeModel treemodel) {
    super(treemodel);
  }

  public DnDAwareTree(final TreeNode root) {
    super(root);
  }

  public void enableDnd(Disposable parent) {
    myDnDEnabler = new DnDEnabler(this, parent);
    Disposer.register(parent, myDnDEnabler);
  }

  public void processMouseEvent(final MouseEvent e) {
//todo [kirillk] to delegate this to DnDEnabler
    if (getToolTipText() == null && e.getID() == MouseEvent.MOUSE_ENTERED) return;
    super.processMouseEvent(e);
  }

  public final boolean isOverSelection(final Point point) {
    final TreePath path = getPathForLocation(point.x, point.y);
    if (path == null) return false;
    return isPathSelected(path);
  }

  @NotNull
  public final JComponent getComponent() {
    return this;
  }

}

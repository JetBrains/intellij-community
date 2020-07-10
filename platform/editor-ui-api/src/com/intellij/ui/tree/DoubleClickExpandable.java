// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 * This interface is intended for a tree node so that double click on it
 * navigates to the source code instead of node expanding/collapsing.
 *
 * @see com.intellij.util.EditSourceOnDoubleClickHandler#isToggleEvent
 */
public interface DoubleClickExpandable {
  /**
   * @return {@code true} to expand/collapse the node, {@code false} to navigate to source if possible
   */
  boolean expandOnDoubleClick();

  static boolean isExpandOnDoubleClickAllowed(@Nullable TreePath path) {
    if (path == null || Registry.is("ide.tree.expand.on.double.click.disabled", false)) return false;
    Object component = path.getLastPathComponent();
    if (component instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)component;
      component = node.getUserObject(); // TreeUtil.getLastUserObject(path)
    }
    if (component instanceof DoubleClickExpandable) {
      DoubleClickExpandable expandable = (DoubleClickExpandable)component;
      return expandable.expandOnDoubleClick();
    }
    return true;
  }
}

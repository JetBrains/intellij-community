// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.treeStructure.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @see TreeExpander#canCollapse
 * @see TreeExpander#collapseAll
 * @see TreeExpander#isCollapseAllVisible
 * @see com.intellij.ide.actions.CollapseAllAction
 * @see com.intellij.openapi.actionSystem.PlatformDataKeys#TREE_EXPANDER
 * @deprecated use {@link TreeExpander} instead
 */
@Deprecated
public class CollapseAllAction extends AnAction implements DumbAware {

  protected JTree myTree;
  protected final int collapseToLevel;

  public CollapseAllAction(JTree tree) {
    this(tree, 0);
  }

  public CollapseAllAction(JTree tree, int collapseToLevel) {
    super(IdeBundle.messagePointer("action.CollapseAllAction.text.collapse.all"), () -> "", AllIcons.Actions.Collapseall);
    this.collapseToLevel = collapseToLevel;
    myTree = tree;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    int row = getTree().getRowCount() - 1;
    while (row >= collapseToLevel) {
      getTree().collapseRow(row);
      row--;
    }
  }

  protected JTree getTree() {
    return myTree;
  }
}

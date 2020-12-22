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
 * @see TreeExpander#canExpand
 * @see TreeExpander#expandAll
 * @see TreeExpander#isExpandAllVisible
 * @see com.intellij.ide.actions.ExpandAllAction
 * @see com.intellij.openapi.actionSystem.PlatformDataKeys#TREE_EXPANDER
 * @deprecated use {@link TreeExpander} instead
 */
@Deprecated
public class ExpandAllAction extends AnAction implements DumbAware {

  protected JTree myTree;

  public ExpandAllAction(JTree tree) {
    super(IdeBundle.messagePointer("action.ExpandAllAction.text.expand.all"), () -> "", AllIcons.Actions.Expandall);
    myTree = tree;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    for (int i = 0; i < getTree().getRowCount(); i++) {
      getTree().expandRow(i);
    }
  }

  protected JTree getTree() {
    return myTree;
  }
}

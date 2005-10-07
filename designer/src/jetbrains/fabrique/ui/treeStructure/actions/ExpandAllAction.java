/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.ui.treeStructure.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.*;

public class ExpandAllAction extends AnAction {

  private JTree myTree;

  public ExpandAllAction(JTree tree) {
    super("Expand All", "", icons.inspector.expandall.png.getIcon());
    myTree = tree;
  }

  public void actionPerformed(AnActionEvent e) {
    for (int i = 0; i < myTree.getRowCount(); i++) {
      myTree.expandRow(i);
    }
  }
}

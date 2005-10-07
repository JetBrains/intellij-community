/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.ui.treeStructure.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

import javax.swing.*;

public class CollapseAllAction extends AnAction {

  private JTree myTree;

  public CollapseAllAction(JTree tree) {
    super("Collapse All", "", icons.inspector.collapseall.png.getIcon());
    myTree = tree;
  }

  public void actionPerformed(AnActionEvent e) {
    int row = getTree().getRowCount() - 1;
    while (row >= 0) {
      getTree().collapseRow(row);
      row--;
    }
  }

  protected JTree getTree() {
    return myTree;
  }
}

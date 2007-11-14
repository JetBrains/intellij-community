package com.intellij.ide.actions.tree;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.util.ui.treetable.TreeTable;

import javax.swing.*;

abstract class BaseTreeNodeAction extends AnAction {
  public BaseTreeNodeAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    Object sourceComponent = getSourceComponent(e);
    if (sourceComponent instanceof JTree)
      performOn((JTree)sourceComponent);
    else if (sourceComponent instanceof TreeTable)
      performOn(((TreeTable)sourceComponent).getTree());
  }

  protected abstract void performOn(JTree tree);

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(enabledOn(getSourceComponent(e)));
  }

  private boolean enabledOn(Object sourceComponent) {
    if (sourceComponent instanceof JTree) return true;
    if (sourceComponent instanceof TreeTable) return true;
    return false;
  }

  private static Object getSourceComponent(AnActionEvent e) {
    return e.getDataContext().getData(DataConstants.CONTEXT_COMPONENT);
  }
}

package com.intellij.ide.actions;

import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 19, 2004
 * Time: 7:38:56 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class TreeCollapseAllActionBase extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    TreeExpander expander = getExpander(e.getDataContext());
    if (expander == null) return;
    if (!expander.canCollapse()) return;
    expander.collapseAll();
  }

  protected abstract TreeExpander getExpander(DataContext dataContext);

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    TreeExpander expander = getExpander(event.getDataContext());
    presentation.setEnabled(expander != null && expander.canCollapse());
  }
}

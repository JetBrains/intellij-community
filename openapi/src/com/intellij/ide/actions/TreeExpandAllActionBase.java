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
 * Time: 7:41:17 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class TreeExpandAllActionBase extends AnAction {
  protected abstract TreeExpander getExpander(DataContext dataContext);

  public final void actionPerformed(AnActionEvent e) {
    TreeExpander expander = getExpander(e.getDataContext());
    if (expander == null) return;
    if (!expander.canExpand()) return;
    expander.expandAll();
  }

  public final void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    TreeExpander expander = getExpander(event.getDataContext());
    presentation.setEnabled(expander != null && expander.canExpand());
  }
}

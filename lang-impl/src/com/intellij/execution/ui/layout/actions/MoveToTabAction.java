package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.layout.impl.Grid;
import com.intellij.execution.ui.layout.impl.ViewContext;
import com.intellij.execution.ui.layout.impl.Tab;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;

public class MoveToTabAction extends BaseRunnerViewAction {
  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    if (!context.isMoveToGridActionEnabled() || content.length != 1) {
      setEnabled(e, false);
      return;
    }
    if (isDetached(context, content[0])) {
      setEnabled(e, false);
      return;
    }

    Grid grid = context.findGridFor(content[0]);
    Tab tab = context.getTabFor(grid);

    if (ViewContext.TAB_TOOLBAR_PLACE.equals(e.getPlace())) {
      setEnabled(e, false);
    } else {
      setEnabled(e, tab.isDefault());
    }
  }

  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    context.getCellTransform().moveToTab(content[0]);
  }
}
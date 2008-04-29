package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.layout.impl.GridImpl;
import com.intellij.execution.ui.layout.impl.TabImpl;
import com.intellij.execution.ui.layout.impl.ViewContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;

public class MoveToGridAction extends BaseRunnerViewAction {
  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    if (!context.isMoveToGridActionEnabled() || content.length != 1) {
      setEnabled(e, false);
      return;
    }

    if (isDetached(context, content[0])) {
      setEnabled(e, false);
      return;
    }

    GridImpl grid = context.findGridFor(content[0]);
    TabImpl tab = context.getTabFor(grid);
    setEnabled(e, !tab.isDefault() && grid.getContents().size() == 1);
  }
                     
  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    context.getCellTransform().moveToGrid(content[0]);
  }
}

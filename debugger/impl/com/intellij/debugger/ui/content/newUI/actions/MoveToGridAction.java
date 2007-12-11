package com.intellij.debugger.ui.content.newUI.actions;

import com.intellij.debugger.ui.content.newUI.ViewContext;
import com.intellij.debugger.ui.content.newUI.Grid;
import com.intellij.debugger.ui.content.newUI.Tab;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;

public class MoveToGridAction extends BaseDebuggerViewAction {
  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    if (content.length != 1) {
      e.getPresentation().setEnabled(false);
      return;
    }

    if (isDetached(context, content[0])) {
      e.getPresentation().setEnabled(false);
      return;
    }

    Grid grid = context.findGridFor(content[0]);
    Tab tab = context.getTabFor(grid);
    e.getPresentation().setEnabled(!tab.isDefault() && grid.getContents().size() == 1);
  }
                     
  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    context.getCellTransform().moveToGrid(content[0]);
  }
}

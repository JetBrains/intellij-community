package com.intellij.debugger.ui.content.newUI.actions;

import com.intellij.debugger.ui.content.newUI.ViewContext;
import com.intellij.debugger.ui.content.newUI.Grid;
import com.intellij.debugger.ui.content.newUI.Tab;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;

public class MoveToTabAction extends BaseDebuggerViewAction {
  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    if (content.length != 1) {
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
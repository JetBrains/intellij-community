package com.intellij.debugger.ui.content.newUI.actions;

import com.intellij.debugger.ui.content.newUI.Grid;
import com.intellij.debugger.ui.content.newUI.Tab;
import com.intellij.debugger.ui.content.newUI.ViewContext;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;

public class CloseViewAction extends BaseDebuggerViewAction {

  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    if (content.length == 0) {
      e.getPresentation().setEnabled(false);
      return;
    }

    Grid grid = context.findGridFor(content[0]);
    Tab tab = context.getTabFor(grid);

    if (ViewContext.TAB_TOOLBAR_PLACE.equals(e.getPlace()) || ViewContext.TAB_POPUP_PLACE.equals(e.getPlace())) {
      e.getPresentation().setEnabled(false);
    } else {
      e.getPresentation().setEnabled(tab.isDefault());
    }
  }

  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    for (Content each : content) {
      context.findCellFor(each).detach();
    }
  }

}

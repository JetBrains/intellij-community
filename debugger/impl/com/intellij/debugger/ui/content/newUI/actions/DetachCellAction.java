package com.intellij.debugger.ui.content.newUI.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.debugger.ui.content.newUI.ViewContext;
import com.intellij.debugger.ui.content.newUI.Grid;
import com.intellij.debugger.ui.content.newUI.Tab;
import com.intellij.ui.content.Content;

public class DetachCellAction extends BaseDebuggerViewAction {
  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    if (content.length == 0 || isDetached(context, content[0])) {
      setEnabled(e, false);
      return;
    }

    Grid grid = context.findGridFor(content[0]);
    Tab tab = context.getTabFor(grid);

    if (ViewContext.TAB_TOOLBAR_PLACE.equals(e.getPlace())) {
      setEnabled(e, false);
    }
    else {
      if (ViewContext.TAB_POPUP_PLACE.equals(e.getPlace())) {
        setEnabled(e, !tab.isDefault() && grid.getContents().size() > 0);
      }
      else {
        setEnabled(e, true);
      }
    }
  }

  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    context.findCellFor(content[0]).detach();
  }
}

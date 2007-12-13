package com.intellij.debugger.ui.content.newUI.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.debugger.ui.content.newUI.ViewContext;
import com.intellij.debugger.ui.content.newUI.Grid;
import com.intellij.debugger.ui.content.newUI.Tab;
import com.intellij.debugger.ui.content.newUI.GridCell;
import com.intellij.ui.content.Content;

public class DetachCellAction extends BaseDebuggerViewAction {
  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    if (content.length == 0 || isDetached(context, content[0])) {
      setEnabled(e, false);
      return;
    }

    Grid grid = context.findGridFor(content[0]);
    Tab tab = context.getTabFor(grid);

    if (ViewContext.TAB_TOOLBAR_PLACE.equals(e.getPlace()) || (ViewContext.TAB_POPUP_PLACE.equals(e.getPlace()))) {
      setEnabled(e, !tab.isDefault() && grid.getContents().size() == 1);
    }
    else {
      if (ViewContext.CELL_TOOLBAR_PLACE.equals(e.getPlace()) && content.length == 1) {
        GridCell cell = grid.getCellFor(content[0]);
        setEnabled(e, cell.getContentCount() == 1);
      } else {
        setEnabled(e, true);
      }
    }
  }

  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    context.findCellFor(content[0]).detach();
  }
}

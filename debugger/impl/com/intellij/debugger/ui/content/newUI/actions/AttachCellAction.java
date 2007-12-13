package com.intellij.debugger.ui.content.newUI.actions;

import com.intellij.debugger.ui.content.newUI.Grid;
import com.intellij.debugger.ui.content.newUI.GridCell;
import com.intellij.debugger.ui.content.newUI.ViewContext;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;

public class AttachCellAction extends BaseDebuggerViewAction {

  protected void update(final AnActionEvent e, final ViewContext context, final Content[] content) {
    if (content.length == 0 || !isDetached(context, content[0])) {
      setEnabled(e, false);
      return;
    }

    Grid grid = context.findGridFor(content[0]);

    GridCell cell = grid.getCellFor(content[0]);
    if (ViewContext.CELL_TOOLBAR_PLACE.equals(e.getPlace()) && content.length == 1) {
      setEnabled(e, cell.getContentCount() == 1);
    } else {
      setEnabled(e, true);
      if (cell.getContentCount() > 1) {
        e.getPresentation().setText(ActionsBundle.message("action.Debugger.AttachCells.text", cell.getContentCount()));
      }
    }
  }

  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    context.findCellFor(content[0]).attach();
  }
}

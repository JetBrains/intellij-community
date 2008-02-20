package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.layout.impl.Grid;
import com.intellij.execution.ui.layout.impl.GridCell;
import com.intellij.execution.ui.layout.impl.Tab;
import com.intellij.execution.ui.layout.impl.ViewContext;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.content.Content;

public class DetachCellAction extends BaseRunnerViewAction {
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
      GridCell cell = grid.getCellFor(content[0]);
      if (ViewContext.CELL_TOOLBAR_PLACE.equals(e.getPlace()) && content.length == 1) {
        setEnabled(e, cell.getContentCount() == 1);
      } else {
        setEnabled(e, true);
        if (cell.getContentCount() > 1) {
          e.getPresentation().setText(ActionsBundle.message("action.Runner.DetachCells.text", cell.getContentCount()));
        }
      }
    }
  }

  protected void actionPerformed(final AnActionEvent e, final ViewContext context, final Content[] content) {
    context.findCellFor(content[0]).detach();
  }
}

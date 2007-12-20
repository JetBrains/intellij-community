package com.intellij.openapi.diff.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.util.FocusDiffSide;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;

abstract class DiffWalkerAction extends AnAction {
  protected DiffWalkerAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent event) {
    FocusDiffSide side = DiffUtil.getFocusDiffSide(event.getDataContext());
    if (side == null) return;
    int line = getLineNumberToGo(side);
    Editor editor = side.getEditor();
    if (line >= 0 && editor != null) {
      LogicalPosition pos = new LogicalPosition(line, 0);
      editor.getCaretModel().moveToLogicalPosition(pos);
      editor.getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }
  }

  public void update(AnActionEvent event) {
    FocusDiffSide side = DiffUtil.getFocusDiffSide(event.getDataContext());
    Presentation presentation = event.getPresentation();
    if (side == null) {
      presentation.setEnabled(false);
    } else presentation.setEnabled(getLineNumberToGo(side) >= 0);
  }

  protected abstract int getLineNumberToGo(FocusDiffSide side);
}

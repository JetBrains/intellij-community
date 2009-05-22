/*
 * @author max
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.TextRange;

class MoveCaretLeftOrRightHandler extends EditorActionHandler {
  enum Direction {LEFT, RIGHT}

  private final Direction myDirection;

  MoveCaretLeftOrRightHandler(Direction direction) {
    myDirection = direction;
  }

  public void execute(Editor editor, DataContext dataContext) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    final CaretModel caretModel = editor.getCaretModel();
    ScrollingModel scrollingModel = editor.getScrollingModel();

    if (selectionModel.hasSelection()) {
      int start = selectionModel.getSelectionStart();
      int end = selectionModel.getSelectionEnd();

      int leftGuard = start + (myDirection == Direction.LEFT ? 1 : 0);
      int rightGuard = end - (myDirection == Direction.RIGHT ? 1 : 0);
      
      if (TextRange.from(leftGuard, rightGuard - leftGuard + 1).contains(caretModel.getOffset())) { // See IDEADEV-36957
        selectionModel.removeSelection();
        caretModel.moveToOffset(myDirection == Direction.RIGHT ? end : start);
        scrollingModel.scrollToCaret(ScrollType.RELATIVE);
        return;
      }
    }

    caretModel.moveCaretRelatively(myDirection == Direction.RIGHT ? 1 : -1, 0, false, false, true);
  }
}

package com.intellij.codeInsight.template.impl.editorActions;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.TextRange;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;

public abstract class HomeEndHandler extends EditorActionHandler {
  private EditorActionHandler myOriginalHandler;
  boolean myIsHomeHandler;

  public HomeEndHandler(final EditorActionHandler originalHandler, boolean isHomeHandler) {
    myOriginalHandler = originalHandler;
    myIsHomeHandler = isHomeHandler;
  }

  public void execute(Editor editor, DataContext dataContext) {
    final TemplateState templateState = TemplateManagerImpl.getTemplateState(editor);
    if (templateState != null && !templateState.isFinished()) {
      final TextRange range = templateState.getCurrentVariableRange();
      final int caretOffset = editor.getCaretModel().getOffset();
      if (range != null && range.getStartOffset() <= caretOffset && caretOffset <= range.getEndOffset()) {
        int offsetToMove = myIsHomeHandler ? range.getStartOffset() : range.getEndOffset();
        if (offsetToMove != caretOffset) {
          editor.getCaretModel().moveToOffset(offsetToMove);
          editor.getSelectionModel().removeSelection();
        }
      } else {
        myOriginalHandler.execute(editor, dataContext);
      }
    } else {
      myOriginalHandler.execute(editor, dataContext);
    }
  }
}

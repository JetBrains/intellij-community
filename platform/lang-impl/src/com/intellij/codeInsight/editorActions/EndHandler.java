// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public final class EndHandler extends EditorActionHandler.ForEachCaret {
  private final EditorActionHandler myOriginalHandler;

  public EndHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  protected void doExecute(final @NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    if (!settings.SMART_END_ACTION) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, caret, dataContext);
      }
      return;
    }

    final Project project = editor.getProject();
    final Document document = editor.getDocument();
    final PsiFile file = project == null ? null : PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (file == null) {
      if (myOriginalHandler != null){
        myOriginalHandler.execute(editor, caret, dataContext);
      }
      return;
    }

    final EditorNavigationDelegate[] extensions = EditorNavigationDelegate.EP_NAME.getExtensions();
    for (EditorNavigationDelegate delegate : extensions) {
      if (delegate.navigateToLineEnd(editor, dataContext) == EditorNavigationDelegate.Result.STOP) {
        return;
      }
    }

    final CaretModel caretModel = editor.getCaretModel();
    final int caretOffset = caretModel.getOffset();
    CharSequence chars = editor.getDocument().getCharsSequence();
    int length = editor.getDocument().getTextLength();

    if (caretOffset < length) {
      final int offset1 = CharArrayUtil.shiftBackward(chars, caretOffset - 1, " \t");
      if (offset1 < 0 || chars.charAt(offset1) == '\n' || chars.charAt(offset1) == '\r') {
        final int offset2 = CharArrayUtil.shiftForward(chars, offset1 + 1, " \t");
        boolean isEmptyLine = offset2 >= length || chars.charAt(offset2) == '\n' || chars.charAt(offset2) == '\r';
        if (isEmptyLine) {

          // There is a possible case that indent string is not calculated for particular document (that is true at least for plain text
          // documents). Hence, we check that and don't finish processing in case we have such a situation.
          boolean stopProcessing = true;
          PsiDocumentManager.getInstance(project).commitAllDocuments();
          CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
          final String lineIndent = styleManager.getLineIndent(file, caretOffset);
          if (lineIndent != null) {
            int col = calcColumnNumber(lineIndent, editor.getSettings().getTabSize(project));
            int line = caretModel.getVisualPosition().line;
            caretModel.moveToVisualPosition(new VisualPosition(line, col));

            if (caretModel.getLogicalPosition().column != col){
              if (!ApplicationManager.getApplication().isWriteAccessAllowed() &&
                  !FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) {
                return;
              }
              editor.getSelectionModel().removeSelection();
              WriteAction.run(() -> document.replaceString(offset1 + 1, offset2, lineIndent));
            }
          }
          else {
            stopProcessing = false;
          }

          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          editor.getSelectionModel().removeSelection();
          if (stopProcessing) {
            return;
          }
        }
      }
    }

    if (myOriginalHandler != null){
      myOriginalHandler.execute(editor, caret, dataContext);
    }
  }

  private static int calcColumnNumber(final String lineIndent, final int tabSize) {
    int result = 0;
    for (char c : lineIndent.toCharArray()) {
      if (c == ' ') result++;
      if (c == '\t') result += tabSize;
    }
    return result;
  }

}

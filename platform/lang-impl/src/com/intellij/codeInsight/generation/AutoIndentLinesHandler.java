// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.DocumentUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class AutoIndentLinesHandler implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance(AutoIndentLinesHandler.class);

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    Document document = editor.getDocument();
    int startOffset;
    int endOffset;
    boolean hasSelection = editor.getSelectionModel().hasSelection();
    if (hasSelection){
      startOffset = editor.getSelectionModel().getSelectionStart();
      endOffset = editor.getSelectionModel().getSelectionEnd() - 1;
    }
    else{
      startOffset = endOffset = editor.getCaretModel().getOffset();
    }
    int line1 = editor.offsetToLogicalPosition(startOffset).line;
    int col = editor.getCaretModel().getLogicalPosition().column;

    try{
      adjustLineIndent(psiFile, document, startOffset, endOffset, line1, project);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
    }

    if (!hasSelection){
      if (line1 < document.getLineCount() - 1){
        if (document.getLineStartOffset(line1 + 1) + col >= document.getTextLength()) {
          col = document.getLineEndOffset(line1 + 1) - document.getLineStartOffset(line1 + 1);
        }
        LogicalPosition pos = new LogicalPosition(line1 + 1, col);
        editor.getCaretModel().moveToLogicalPosition(pos);
        editor.getSelectionModel().removeSelection();
        editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      }
    }
  }

  private static void adjustLineIndent(PsiFile file,
                                Document document,
                                int startOffset, int endOffset, int line, Project project) {
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    if (startOffset == endOffset) {
      int lineStart = document.getLineStartOffset(line);
      if (codeStyleManager.isLineToBeIndented(file, lineStart)) {
        codeStyleManager.adjustLineIndent(file, lineStart);
      }
    } else {
      codeStyleManager.adjustLineIndent(file, new TextRange(DocumentUtil.getLineStartOffset(startOffset, document), endOffset));
    }
  }
}

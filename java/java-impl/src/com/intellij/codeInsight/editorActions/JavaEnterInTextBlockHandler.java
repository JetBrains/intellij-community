// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.editorActions.enter.EnterInStringLiteralHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaEnterInTextBlockHandler extends EnterInStringLiteralHandler {

  @Override
  public Result postProcessEnter(@NotNull PsiFile file, @NotNull Editor editor, @NotNull DataContext dataContext) {
    if (!(file instanceof PsiJavaFile)) return super.postProcessEnter(file, editor, dataContext);
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
    PsiLiteralExpressionImpl textBlock = getTextBlock(file, editor.getCaretModel().getOffset());
    if (textBlock == null) return super.postProcessEnter(file, editor, dataContext);
    int offset = editor.getCaretModel().getOffset();
    Document document = editor.getDocument();
    if (isContentAtTheEnd(textBlock)) {
      CodeStyleManager.getInstance(textBlock.getProject()).reformat(textBlock);
      offset = editor.getCaretModel().getOffset();
    }
    else if (!isAtBlockStart(document, textBlock, offset)) {
      return super.postProcessEnter(file, editor, dataContext);
    }
    editor.getCaretModel().moveToOffset(findOffset(editor, document, textBlock, offset));
    return Result.Continue;
  }

  private static boolean isContentAtTheEnd(@NotNull PsiLiteralExpressionImpl textBlock) {
    String text = textBlock.getTextBlockText();
    if (text == null) return false;
    boolean foundContent = false;
    for (int i = 0; i < text.length(); i++) {
      if (!isWhitespaceOrNewLine(text.charAt(i))) {
        foundContent = true;
      }
      else if (foundContent) {
        return false;
      }
    }
    return true;
  }

  private static boolean isAtBlockStart(@NotNull Document document, @NotNull PsiLiteralExpressionImpl textBlock, int offset) {
    String text = document.getText(new TextRange(textBlock.getTextOffset(), offset));
    if (!text.startsWith("\"\"\"")) return false;
    text = text.substring(3);
    return text.chars().allMatch(JavaEnterInTextBlockHandler::isWhitespaceOrNewLine);
  }

  private static boolean isWhitespaceOrNewLine(int c) {
    return Character.isWhitespace(c) || c == '\n';
  }

  private static int findOffset(@NotNull Editor editor,
                                @NotNull Document document,
                                @NotNull PsiLiteralExpressionImpl textBlock,
                                int offset) {
    TextRange afterOffset = new TextRange(offset, textBlock.getTextRange().getEndOffset());
    String text = document.getText(afterOffset);
    int line = document.getLineNumber(offset);
    int lineStart = document.getLineStartOffset(line);
    int start = text.indexOf('\n') + 1;
    for (int i = start; i < text.length(); i++) {
      char c = text.charAt(i);
      if (isWhitespaceOrNewLine(c)) continue;
      int column = editor.offsetToLogicalPosition(offset + i).column;
      String indent = StringUtil.repeatSymbol(' ', column);
      document.replaceString(lineStart, offset, indent);
      return editor.logicalPositionToOffset(new LogicalPosition(line, column));
    }
    return offset;
  }

  @Nullable
  private static PsiLiteralExpressionImpl getTextBlock(@NotNull PsiFile file, int offset) {
    PsiJavaToken token = ObjectUtils.tryCast(file.findElementAt(offset), PsiJavaToken.class);
    if (token == null || token.getTokenType() != JavaTokenType.TEXT_BLOCK_LITERAL) return null;
    return ObjectUtils.tryCast(token.getParent(), PsiLiteralExpressionImpl.class);
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.simple;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ModNavigator;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLoopStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class RParenthTailType extends ModNavigatorTailType {
  private static final Logger LOG = Logger.getInstance(RParenthTailType.class);

  private static TextRange getRangeToCheckParensBalance(PsiFile file, final Document document, int startOffset){
    PsiElement element = file.findElementAt(startOffset);
    element = PsiTreeUtil.getParentOfType(element, PsiStatement.class, false);
    if (element != null) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiLoopStatement) {
        element = parent;
      }
    }
    return element == null ? new TextRange(0, document.getTextLength()) : element.getTextRange();
  }

  protected abstract boolean isSpaceWithinParentheses(CommonCodeStyleSettings styleSettings, Document document, final int tailOffset);

  @Override
  public int processTail(@NotNull ModNavigator navigator, int tailOffset) {
    Document document = navigator.getDocument();
    PsiFile psiFile = navigator.getPsiFile();
    Language language = PsiUtilCore.getLanguageAtOffset(psiFile, tailOffset);
    CommonCodeStyleSettings settings = CodeStyle.getLanguageSettings(psiFile, language);
    return addRParenth(navigator, tailOffset,
                       isSpaceWithinParentheses(settings, document, tailOffset));
  }

  public static int addRParenth(ModNavigator navigator, int offset, boolean spaceWithinParens) {
    int existingRParenthOffset = getExistingRParenthOffset(navigator, offset);

    if (existingRParenthOffset < 0){
      if (spaceWithinParens){
        offset = insertChar(navigator, offset, ' ');
      }
      navigator.getDocument().insertString(offset, ")");
      return moveCaret(navigator, offset, 1);
    }
    if (spaceWithinParens && offset == existingRParenthOffset) {
      existingRParenthOffset = insertChar(navigator, offset, ' ');
    }
    return moveCaret(navigator, existingRParenthOffset, 1);
  }

  @Override
  public @NonNls String toString() {
    return "RParenth";
  }

  private static int getExistingRParenthOffset(@NotNull ModNavigator navigator, final int tailOffset) {
    final Document document = navigator.getDocument();
    if (tailOffset >= document.getTextLength()) return -1;
    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(navigator.getProject());
    psiDocumentManager.commitDocument(document);
    PsiFile file = navigator.getPsiFile();

    int existingRParenthOffset = -1;

    for (PsiElement element = file.findElementAt(tailOffset); element != null; element = PsiTreeUtil.nextLeaf(element)) {
      if (PsiUtil.isJavaToken(element, JavaTokenType.RPARENTH)) {
        existingRParenthOffset = element.getTextRange().getStartOffset();
      }
      if (!(element instanceof PsiComment) && !(element instanceof PsiWhiteSpace)) {
        break;
      }
    }

    if (existingRParenthOffset >= 0){
      TextRange range = getRangeToCheckParensBalance(file, document, navigator.getCaretOffset());
      int balance = calcParensBalance(document, file, range.getStartOffset(), range.getEndOffset());
      if (balance > 0){
        return -1;
      }
    }
    return existingRParenthOffset;
  }

  private static int calcParensBalance(Document document, PsiFile file, int rangeStart, int rangeEnd) {
    LOG.assertTrue(0 <= rangeStart);
    LOG.assertTrue(rangeStart <= rangeEnd);
    LOG.assertTrue(rangeEnd <= document.getTextLength());

    int balance = 0;
    for (PsiElement element = file.findElementAt(rangeStart);
         element != null && element.getTextRange().getStartOffset() < rangeEnd;
         element = PsiTreeUtil.nextLeaf(element)) {
      if (PsiUtil.isJavaToken(element, JavaTokenType.LPARENTH)) {
        balance++;
      }
      else if (PsiUtil.isJavaToken(element, JavaTokenType.RPARENTH)) {
        balance--;
      }
    }
    return balance;
  }

}

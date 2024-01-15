// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.unicode;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFragment;
import com.intellij.psi.PsiLiteralExpression;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementContextPredicate;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class ReplaceOctalEscapeWithUnicodeEscapeIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.octal.escape.with.unicode.escape.intention.family.name");
  }

  @Override
  protected @Nullable String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("replace.octal.escape.with.unicode.escape.intention.name");
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    TextRange selection = context.selection();
    if (!selection.isEmpty()) {
      // does not check if octal escape is inside char or string literal (garbage in, garbage out)
      final Document document = element.getContainingFile().getFileDocument();
      while (selection.getEndOffset() < document.getTextLength()) {
        char nextChar = document.getCharsSequence().charAt(selection.getEndOffset());
        if (nextChar >= '0' && nextChar <= '7') {
          selection = selection.grown(1);
        } else {
          break;
        }
      }
      final String text = document.getText(selection);
      final int textLength = selection.getLength();
      final StringBuilder replacement = new StringBuilder(textLength);
      int anchor = 0;
      while (true) {
        final int index = indexOfOctalEscape(text, anchor + 1);
        if (index < 0) {
          break;
        }
        replacement.append(text, anchor, index);
        anchor = appendUnicodeEscape(text, index, replacement);
      }
      replacement.append(text, anchor, textLength);
      final int start = selection.getStartOffset();
      final int end = selection.getEndOffset();
      document.replaceString(start, end, replacement);
    }
    else if (element instanceof PsiLiteralExpression literalExpression) {
      final String newLiteralText = buildReplacementText(element, context);
      PsiReplacementUtil.replaceExpression(literalExpression, newLiteralText);
    }
    else if (element instanceof PsiFragment fragment) {
      final String newFragmentText = buildReplacementText(element, context);
      PsiReplacementUtil.replaceFragment(fragment, newFragmentText);
    }
  }

  @NotNull
  private static String buildReplacementText(@NotNull PsiElement element, @NotNull ActionContext context) {
    final String text = element.getText();
    final int offset = context.offset() - element.getTextOffset();
    final StringBuilder newLiteralText = new StringBuilder();
    final int index1 = indexOfOctalEscape(text, offset);
    final int index2 = indexOfOctalEscape(text, offset + 1);
    final int escapeStart = index2 == offset ? index2 : index1;
    newLiteralText.append(text, 0, escapeStart);
    final int escapeEnd = appendUnicodeEscape(text, escapeStart, newLiteralText);
    newLiteralText.append(text.substring(escapeEnd));
    return newLiteralText.toString();
  }

  private static int appendUnicodeEscape(String text, int escapeStart, @NonNls StringBuilder out) {
    final int textLength = text.length();
    int length = 1;
    boolean zeroToThree = false;
    while (escapeStart + length <= textLength) {
      final char c = escapeStart + length == textLength ? 0 : text.charAt(escapeStart + length);
      if (length == 1 && (c == '0' || c == '1' || c == '2' || c == '3')) {
        zeroToThree = true;
      }
      if (c < '0' || c > '7' || length > 3 || (length > 2 && !zeroToThree)) {
        final int ch = Integer.parseInt(text.substring(escapeStart + 1, escapeStart + length), 8);
        out.append("\\u").append(String.format("%04x", Integer.valueOf(ch)));
        break;
      }
      length++;
    }
    return escapeStart + length;
  }

  private static int indexOfOctalEscape(String text, int offset) {
    final int textLength = text.length();
    int escapeStart = -1;
    outer: while (true) {
      escapeStart = text.indexOf('\\', escapeStart + 1);
      if (escapeStart < 0) {
        break;
      }
      if (escapeStart < offset - 4 || escapeStart >= textLength - 1 || text.charAt(escapeStart + 1) == '\\') {
        continue;
      }
      boolean isEscape = true;
      int previousChar = escapeStart - 1;
      while (previousChar >= 0 && text.charAt(previousChar) == '\\') {
        isEscape = !isEscape;
        previousChar--;
      }
      if (!isEscape) {
        continue;
      }
      int length = 1;
      // see JLS 3.10.6. Escape Sequences for Character and String Literals
      boolean zeroToThree = false;
      while (escapeStart + length < textLength) {
        final char c = text.charAt(escapeStart + length);
        if (length == 1 && (c == '0' || c == '1' || c == '2' || c == '3')) {
          zeroToThree = true;
        }
        if (c < '0' || c > '7' || length > 3 || (length > 2 && !zeroToThree)) {
          if (offset <= escapeStart + length && length > 1) {
            return escapeStart;
          }
          continue outer;
        }
        length++;
      }
      return escapeStart;
    }
    return -1;
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new OctalEscapePredicate();
  }

  private static class OctalEscapePredicate extends PsiElementContextPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element, @NotNull ActionContext context) {
      TextRange selection = context.selection();
      if (!selection.isEmpty()) {
        final int start = selection.getStartOffset();
        final int end = selection.getEndOffset();
        final String text = element.getContainingFile().getFileDocument()
          .getCharsSequence().subSequence(start, end).toString();
        return indexOfOctalEscape(text, 1) >= 0;
      }
      else if (element instanceof PsiLiteralExpression || element instanceof PsiFragment) {
        final String text = element.getText();
        final int offset = context.offset() - element.getTextOffset();
        final int index = indexOfOctalEscape(text, offset);
        return index >= 0 && offset >= index;
      }
      return false;
    }
  }
}

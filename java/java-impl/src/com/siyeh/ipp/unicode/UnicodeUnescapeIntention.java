// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.unicode;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementContextPredicate;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class UnicodeUnescapeIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("unicode.unescape.intention.family.name");
  }

  @Override
  protected @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("unicode.unescape.intention.name");
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, @NotNull ActionContext context, @NotNull ModPsiUpdater updater) {
    TextRange selection = context.selection();
    final Document document = element.getContainingFile().getFileDocument();
    if (!selection.isEmpty()) {
      // does not check if Unicode escape is inside char or string literal (garbage in, garbage out)
      final String text = document.getText(selection);
      final int textLength = selection.getLength();
      final StringBuilder replacement = new StringBuilder(textLength);
      int anchor = 0;
      while (true) {
        final int index = indexOfUnicodeEscape(text, anchor + 1);
        if (index < 0) {
          break;
        }
        replacement.append(text, anchor, index);
        int hexStart = index + 1;
        while (text.charAt(hexStart) == 'u') {
          hexStart++;
        }
        anchor = hexStart + 4;
        final int c = Integer.parseInt(text.substring(hexStart, anchor), 16);
        replacement.appendCodePoint(c);
      }
      replacement.append(text, anchor, textLength);
      final int start = selection.getStartOffset();
      final int end = selection.getEndOffset();
      document.replaceString(start, end, replacement);
    }
    else {
      final int lineNumber = document.getLineNumber(context.offset());
      final int lineStartOffset = document.getLineStartOffset(lineNumber);
      final String line = document.getText(new TextRange(lineStartOffset, document.getLineEndOffset(lineNumber)));
      final int column = context.offset() - lineStartOffset;
      final int index1 = indexOfUnicodeEscape(line, column);
      final int index2 = indexOfUnicodeEscape(line, column + 1);
      // if the caret is between two unicode escapes, replace the one to the right
      final int escapeStart = index2 == column ? index2 : index1;
      int hexStart = escapeStart + 1;
      while (line.charAt(hexStart) == 'u') {
        hexStart++;
      }
      final char c = (char)Integer.parseInt(line.substring(hexStart, hexStart + 4), 16);
      if (Character.isHighSurrogate(c)) {
        hexStart += 4;
        if (line.charAt(hexStart++) == '\\' && line.charAt(hexStart++) == 'u') {
          while (line.charAt(hexStart) == 'u') hexStart++;
          final char d = (char)Integer.parseInt(line.substring(hexStart, hexStart + 4), 16);
          document.replaceString(lineStartOffset + escapeStart, lineStartOffset + hexStart + 4, String.valueOf(new char[] {c, d}));
          return;
        }
      }
      else if (Character.isLowSurrogate(c)) {
        if (escapeStart >= 6 &&
            StringUtil.isHexDigit(line.charAt(escapeStart - 1)) && StringUtil.isHexDigit(line.charAt(escapeStart - 2)) &&
            StringUtil.isHexDigit(line.charAt(escapeStart - 3)) && StringUtil.isHexDigit(line.charAt(escapeStart - 4))) {
          int i = escapeStart - 5;
          while (i > 0 && line.charAt(i) == 'u') i--;
          if (line.charAt(i) == '\\' && (i == 0 || line.charAt(i - 1) != '\\')) {
            final char b = (char)Integer.parseInt(line.substring(escapeStart - 4, escapeStart), 16);
            document.replaceString(lineStartOffset + i, lineStartOffset + hexStart + 4, String.valueOf(new char[] {b, c}));
            return;
          }
        }
      }
      document.replaceString(lineStartOffset + escapeStart, lineStartOffset + hexStart + 4, String.valueOf(c));
    }
  }

  /**
   * see JLS 3.3. Unicode Escapes
   */
  static int indexOfUnicodeEscape(@NotNull String text, int offset) {
    final int length = text.length();
    for (int i = 0; i < length; i++) {
      final char c = text.charAt(i);
      if (c != '\\') {
        continue;
      }
      boolean isEscape = true;
      int previousChar = i - 1;
      while (previousChar >= 0 && text.charAt(previousChar) == '\\') {
        isEscape = !isEscape;
        previousChar--;
      }
      if (!isEscape) {
        continue;
      }
      int nextChar = i;
      do {
        nextChar++;
        if (nextChar >= length) {
          break;
        }
      }
      while (text.charAt(nextChar) == 'u'); // \uuuuFFFD is a legal unicode escape
      if (nextChar == i + 1 || nextChar + 3 >= length) {
        break;
      }
      if (StringUtil.isHexDigit(text.charAt(nextChar)) &&
          StringUtil.isHexDigit(text.charAt(nextChar + 1)) &&
          StringUtil.isHexDigit(text.charAt(nextChar + 2)) &&
          StringUtil.isHexDigit(text.charAt(nextChar + 3))) {
        final int escapeEnd = nextChar + 4;
        if (offset <= escapeEnd) {
          final char d = (char)Integer.parseInt(text.substring(nextChar, nextChar + 4), 16);
          if (d == '\r') return -1; // carriage return not allowed
          return i;
        }
      }
    }
    return -1;
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new UnicodeEscapePredicate();
  }

  private static class UnicodeEscapePredicate extends PsiElementContextPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element, @NotNull ActionContext context) {
      TextRange selection = context.selection();
      Document document = element.getContainingFile().getFileDocument();
      if (!selection.isEmpty()) {
        final int start = selection.getStartOffset();
        final int end = selection.getEndOffset();
        if (start < 0 || end < 0 || start > end || end > document.getTextLength()) {
          return false;
        }
        final String text = document.getCharsSequence().subSequence(start, end).toString();
        return indexOfUnicodeEscape(text, 1) >= 0;
      }
      else {
        final int lineNumber = document.getLineNumber(context.offset());
        int lineStart = document.getLineStartOffset(lineNumber);
        final String line = document.getText(new TextRange(lineStart, document.getLineEndOffset(lineNumber)));
        final int column = context.offset() - lineStart;
        final int index = indexOfUnicodeEscape(line, column);
        return index >= 0 && column >= index;
      }
    }
  }
}

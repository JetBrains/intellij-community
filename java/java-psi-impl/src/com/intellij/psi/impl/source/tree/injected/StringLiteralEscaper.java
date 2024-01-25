/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class StringLiteralEscaper<T extends PsiLanguageInjectionHost> extends LiteralTextEscaper<T> {
  private int[] outSourceOffsets;

  public StringLiteralEscaper(T host) {
    super(host);
  }

  @Override
  public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
    String subText = rangeInsideHost.substring(myHost.getText());
    outSourceOffsets = new int[subText.length()+1];
    return PsiLiteralExpressionImpl.parseStringCharacters(subText, outChars, outSourceOffsets);
  }

  @Override
  public int getOffsetInHost(int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
    int result = offsetInDecoded < outSourceOffsets.length ? outSourceOffsets[offsetInDecoded] : -1;
    if (result == -1) return -1;
    return Math.min(result, rangeInsideHost.getLength()) + rangeInsideHost.getStartOffset();
  }

  @Override
  public boolean isOneLine() {
    boolean textBlock = myHost instanceof PsiFragment && ((PsiFragment)myHost).isTextBlock() ||
                        myHost instanceof PsiLiteralExpression && ((PsiLiteralExpression)myHost).isTextBlock();
    return !textBlock;
  }

  @Override
  public @NotNull TextRange getRelevantTextRange() {
    if (myHost instanceof PsiFragment) {
      PsiFragment fragment = (PsiFragment)myHost;
      int length = fragment.getTextLength();
      IElementType tokenType = fragment.getTokenType();
      if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN) {
        final String text = fragment.getText();
        int startOffset = findBlockStart(text);
        return startOffset < 0
               ? new TextRange(0, length)
               : new TextRange(startOffset, length - 2); // ends with \{
      }
      else if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_MID) {
        // Begins with } and ends with \{
        return new TextRange(1, Math.max(1, length - 2));
      }
      else if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_END) {
        String text = fragment.getText();
        if (text.endsWith("\"\"\"")) {
          // Begins with } and ends with """
          return new TextRange(1, Math.max(1, length - 3));
        }
        return new TextRange(1, length);
      }
      else if (tokenType == JavaTokenType.STRING_TEMPLATE_BEGIN || tokenType == JavaTokenType.STRING_TEMPLATE_MID) {
        // Begins with " or } and ends with \{
        return new TextRange(1, Math.max(1, length - 2));
      }
      else if (tokenType == JavaTokenType.STRING_TEMPLATE_END) {
        String text = fragment.getText();
        if (text.endsWith("\"")) {
          // Begins with } and ends with "
          return new TextRange(1, Math.max(1, length - 1));
        }
        return new TextRange(1, length);
      }
      throw new IllegalStateException("Unexpected tokenType: " + tokenType);
    }
    else if (myHost instanceof PsiLiteralExpression) {
      PsiLiteralExpression expression = (PsiLiteralExpression)myHost;
      int length = expression.getTextLength();
      if (expression.isTextBlock()) {
        final String text = expression.getText();
        int startOffset = findBlockStart(text);
        return startOffset < 0
               ? new TextRange(0, length)
               : new TextRange(startOffset, length - (text.endsWith("\"\"\"") ? 3 : 0));
      }
      // avoid calling PsiLiteralExpression.getValue(): it allocates new string, it returns null for invalid escapes
      final PsiType type = expression.getType();
      boolean isQuoted = PsiTypes.charType().equals(type) || type != null && type.equalsToText(CommonClassNames.JAVA_LANG_STRING);
      return isQuoted ? new TextRange(1, Math.max(1, length - 1)) : TextRange.from(0, length);
    }
    int textLength = myHost.getTextLength();
    if (textLength >= 2) {
      return TextRange.from(1, textLength - 2);
    }
    else {
      return super.getRelevantTextRange();
    }
  }

  static int findBlockStart(String text) {
    if (!text.startsWith("\"\"\"")) return -1;
    final int length = text.length();
    for (int i = 3; i < length; i++) {
      final char c = text.charAt(i);
      if (c == '\n') return i + 1;
      if (!Character.isWhitespace(c)) return -1;
    }
    return -1;
  }
}

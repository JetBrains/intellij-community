/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.injected.StringLiteralEscaper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.LiteralFormatUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiLiteralExpressionImpl
       extends ExpressionPsiElement
       implements PsiLiteralExpression, PsiLanguageInjectionHost, ContributedReferenceHost {
  @NonNls private static final String QUOT = "&quot;";

  @NonNls public static final String HEX_PREFIX = "0x";
  @NonNls public static final String BIN_PREFIX = "0b";
  @NonNls public static final String _2_IN_31 = Long.toString(-1L << 31).substring(1);
  @NonNls public static final String _2_IN_63 = Long.toString(-1L << 63).substring(1);
  public static final TokenSet INTEGER_LITERALS = TokenSet.create(JavaTokenType.INTEGER_LITERAL, JavaTokenType.LONG_LITERAL);
  public static final TokenSet REAL_LITERALS = TokenSet.create(JavaTokenType.FLOAT_LITERAL, JavaTokenType.DOUBLE_LITERAL);
  public static final TokenSet NUMERIC_LITERALS = TokenSet.orSet(INTEGER_LITERALS, REAL_LITERALS);

  public PsiLiteralExpressionImpl() {
    super(JavaElementType.LITERAL_EXPRESSION);
  }

  @Override
  public PsiType getType() {
    final IElementType type = getLiteralElementType();
    if (type == JavaTokenType.INTEGER_LITERAL) {
      return PsiType.INT;
    }
    if (type == JavaTokenType.LONG_LITERAL) {
      return PsiType.LONG;
    }
    if (type == JavaTokenType.FLOAT_LITERAL) {
      return PsiType.FLOAT;
    }
    if (type == JavaTokenType.DOUBLE_LITERAL) {
      return PsiType.DOUBLE;
    }
    if (type == JavaTokenType.CHARACTER_LITERAL) {
      return PsiType.CHAR;
    }
    if (type == JavaTokenType.STRING_LITERAL) {
      PsiManagerEx manager = getManager();
      GlobalSearchScope resolveScope = ResolveScopeManager.getElementResolveScope(this);
      return PsiType.getJavaLangString(manager, resolveScope);
    }
    if (type == JavaTokenType.TRUE_KEYWORD || type == JavaTokenType.FALSE_KEYWORD) {
      return PsiType.BOOLEAN;
    }
    if (type == JavaTokenType.NULL_KEYWORD) {
      return PsiType.NULL;
    }
    return null;
  }

  public IElementType getLiteralElementType() {
    return getFirstChildNode().getElementType();
  }

  public String getCanonicalText() {
    final TreeElement literal = getFirstChildNode();
    final IElementType type = literal.getElementType();
    return NUMERIC_LITERALS.contains(type) ? LiteralFormatUtil.removeUnderscores(literal.getText()) : literal.getText();
  }

  @Override
  public Object getValue() {
    final IElementType type = getLiteralElementType();
    String text = NUMERIC_LITERALS.contains(type) ? getCanonicalText().toLowerCase() : getCanonicalText();
    final int textLength = text.length();

    if (type == JavaTokenType.INTEGER_LITERAL) {
      try {
        if (text.startsWith(HEX_PREFIX)) {
          // should fit in 32 bits
          final long value = parseDigits(text.substring(2), 4, 32);
          return Integer.valueOf((int)value);
        }
        if (text.startsWith(BIN_PREFIX)) {
          // should fit in 32 bits
          final long value = parseDigits(text.substring(2), 1, 32);
          return Integer.valueOf((int)value);
        }
        if (StringUtil.startsWithChar(text, '0')) {
          // should fit in 32 bits
          final long value = parseDigits(text, 3, 32);
          return Integer.valueOf((int)value);
        }
        final long l = Long.parseLong(text, 10);
        if (text.equals(_2_IN_31)) return Integer.valueOf((int)l);
        long converted = (int)l;
        return l == converted ? Integer.valueOf((int)l) : null;
      }
      catch (NumberFormatException e) {
        return null;
      }
    }
    if (type == JavaTokenType.LONG_LITERAL) {
      if (StringUtil.endsWithChar(text, 'L') || StringUtil.endsWithChar(text, 'l')) {
        text = text.substring(0, textLength - 1);
      }
      try {
        if (text.startsWith(HEX_PREFIX)) {
          return parseDigits(text.substring(2), 4, 64);
        }
        if (text.startsWith(BIN_PREFIX)) {
          return parseDigits(text.substring(2), 1, 64);
        }
        if (StringUtil.startsWithChar(text, '0')) {
          // should fit in 64 bits
          return parseDigits(text, 3, 64);
        }
        if (_2_IN_63.equals(text)) return Long.valueOf(-1L << 63);
        return Long.valueOf(text, 10);
      }
      catch (NumberFormatException e) {
        return null;
      }
    }
    if (type == JavaTokenType.FLOAT_LITERAL) {
      try {
        return Float.valueOf(text);
      }
      catch (NumberFormatException e) {
        return null;
      }
    }
    if (type == JavaTokenType.DOUBLE_LITERAL) {
      try {
        return Double.valueOf(text);
      }
      catch (NumberFormatException e) {
        return null;
      }
    }
    if (type == JavaTokenType.CHARACTER_LITERAL) {
      if (StringUtil.endsWithChar(text, '\'')) {
        if (textLength == 1) return null;
        text = text.substring(1, textLength - 1);
      }
      else {
        text = text.substring(1, textLength);
      }
      StringBuilder chars = new StringBuilder();
      boolean success = parseStringCharacters(text, chars, null);
      if (!success) return null;
      if (chars.length() != 1) return null;
      return Character.valueOf(chars.charAt(0));
    }
    if (type == JavaTokenType.STRING_LITERAL) {
      String innerText = getInnerText();
      return innerText == null ? null : internedParseStringCharacters(innerText);
    }
    if (type == JavaTokenType.TRUE_KEYWORD) {
      return Boolean.TRUE;
    }
    if (type == JavaTokenType.FALSE_KEYWORD) {
      return Boolean.FALSE;
    }

    return null;
  }

  @Nullable
  public String getInnerText() {
    String text = getCanonicalText();
    int textLength = text.length();
    if (StringUtil.endsWithChar(text, '\"')) {
      if (textLength == 1) return null;
      text = text.substring(1, textLength - 1);
    }
    else {
      if (text.startsWith(QUOT) && text.endsWith(QUOT) && textLength > QUOT.length()) {
        text = text.substring(QUOT.length(), textLength - QUOT.length());
      }
      else {
        return null;
      }
    }
    return text;
  }

  // convert text to number according to radix specified
  // if number is more than maxBits bits long, throws NumberFormatException
  private static long parseDigits(final String text, final int bitsInRadix, final int maxBits) throws NumberFormatException {
    final int radix = 1 << bitsInRadix;
    final int textLength = text.length();
    if (textLength == 0) {
      throw new NumberFormatException(text);
    }
    long integer = textLength == 1 ? 0 : Long.parseLong(text.substring(0, textLength - 1), radix);
    if ((integer & (-1L << (maxBits - bitsInRadix))) != 0) {
      throw new NumberFormatException(text);
    }
    final int lastDigit = Character.digit(text.charAt(textLength - 1), radix);
    if (lastDigit == -1) {
      throw new NumberFormatException(text);
    }
    integer <<= bitsInRadix;
    integer |= lastDigit;
    return integer;
  }

  @Nullable
  private static String internedParseStringCharacters(final String chars) {
    final StringBuilder outChars = new StringBuilder(chars.length());
    final boolean success = parseStringCharacters(chars, outChars, null);
    return success ? outChars.toString() : null;
  }

  public static boolean parseStringCharacters(@NotNull String chars, @NotNull StringBuilder outChars, @Nullable int[] sourceOffsets) {
    assert sourceOffsets == null || sourceOffsets.length == chars.length()+1;
    if (chars.indexOf('\\') < 0) {
      outChars.append(chars);
      if (sourceOffsets != null) {
        for (int i = 0; i < sourceOffsets.length; i++) {
          sourceOffsets[i] = i;
        }
      }
      return true;
    }
    int index = 0;
    final int outOffset = outChars.length();
    while (index < chars.length()) {
      char c = chars.charAt(index++);
      if (sourceOffsets != null) {
        sourceOffsets[outChars.length()-outOffset] = index - 1;
        sourceOffsets[outChars.length() + 1 -outOffset] = index;
      }
      if (c != '\\') {
        outChars.append(c);
        continue;
      }
      if (index == chars.length()) return false;
      c = chars.charAt(index++);
      switch (c) {
        case'b':
          outChars.append('\b');
          break;

        case't':
          outChars.append('\t');
          break;

        case'n':
          outChars.append('\n');
          break;

        case'f':
          outChars.append('\f');
          break;

        case'r':
          outChars.append('\r');
          break;

        case'"':
          outChars.append('"');
          break;

        case'\'':
          outChars.append('\'');
          break;

        case'\\':
          outChars.append('\\');
          break;

        case'0':
        case'1':
        case'2':
        case'3':
        case'4':
        case'5':
        case'6':
        case'7':
          char startC = c;
          int v = (int)c - '0';
          if (index < chars.length()) {
            c = chars.charAt(index++);
            if ('0' <= c && c <= '7') {
              v <<= 3;
              v += c - '0';
              if (startC <= '3' && index < chars.length()) {
                c = chars.charAt(index++);
                if ('0' <= c && c <= '7') {
                  v <<= 3;
                  v += c - '0';
                }
                else {
                  index--;
                }
              }
            }
            else {
              index--;
            }
          }
          outChars.append((char)v);
          break;

        case'u':
          // uuuuu1234 is valid too
          while (index != chars.length() && chars.charAt(index) == 'u') {
            index++;
          }
          if (index + 4 <= chars.length()) {
            try {
              int code = Integer.parseInt(chars.substring(index, index + 4), 16);
              //line separators are invalid here
              if (code == 0x000a || code == 0x000d) return false;
              c = chars.charAt(index);
              if (c == '+' || c == '-') return false;
              outChars.append((char)code);
              index += 4;
            }
            catch (Exception e) {
              return false;
            }
          }
          else {
            return false;
          }
          break;

        default:
          return false;
      }
      if (sourceOffsets != null) {
        sourceOffsets[outChars.length()-outOffset] = index;
      }
    }
    return true;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitLiteralExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiLiteralExpression:" + getText();
  }

  @Override
  public boolean isValidHost() {
    return getValue() instanceof String;
  }

  @Override
  @NotNull
  public PsiReference[] getReferences() {
    PsiReference[] references = PsiReferenceService.getService().getContributedReferences(this);
    return references;
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull final String text) {
    TreeElement valueNode = getFirstChildNode();
    assert valueNode instanceof LeafElement;
    ((LeafElement)valueNode).replaceWithText(text);
    return this;
  }

  @Override
  @NotNull
  public LiteralTextEscaper<PsiLiteralExpressionImpl> createLiteralTextEscaper() {
    return new StringLiteralEscaper<PsiLiteralExpressionImpl>(this);
  }
}

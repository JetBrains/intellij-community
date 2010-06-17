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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.StringLiteralEscaper;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PsiLiteralExpressionImpl extends ExpressionPsiElement implements PsiLiteralExpression,PsiLanguageInjectionHost, Constants, ContributedReferenceHost {
  @NonNls private static final String QUOT = "&quot;";
  @NonNls private static final String HEXPREFIX = "0x";
  @NonNls private static final String HEXPREFIX2 = "0X";
  @NonNls private static final String LHEX_PREFIX = "0xl";

  public PsiLiteralExpressionImpl() {
    super(LITERAL_EXPRESSION);
  }

  public PsiType getType() {
    IElementType i = getFirstChildNode().getElementType();
    if (i == INTEGER_LITERAL) {
      return PsiType.INT;
    }
    if (i == LONG_LITERAL) {
      return PsiType.LONG;
    }
    if (i == FLOAT_LITERAL) {
      return PsiType.FLOAT;
    }
    if (i == DOUBLE_LITERAL) {
      return PsiType.DOUBLE;
    }
    if (i == CHARACTER_LITERAL) {
      return PsiType.CHAR;
    }
    if (i == STRING_LITERAL) {
      return PsiType.getJavaLangString(getManager(), getResolveScope());
    }
    if (i == TRUE_KEYWORD || i == FALSE_KEYWORD) {
      return PsiType.BOOLEAN;
    }
    if (i == NULL_KEYWORD) {
      return PsiType.NULL;
    }
    return null;
  }

  @NonNls private static final String _2_IN_63 = Long.toString(-1L << 63).substring(1);
  @NonNls private static final String _2_IN_31 = Long.toString(-1L << 31).substring(1);
  @NonNls private static final String _2_IN_63_L = _2_IN_63 + "l";

  public Object getValue() {
    String text = getFirstChildNode().getText();
    int textLength = text.length();
    IElementType i = getFirstChildNode().getElementType();
    if (i == INTEGER_LITERAL) {
      try {
        if (text.startsWith(HEXPREFIX) || text.startsWith(HEXPREFIX2)) {
          // should fit in 32 bits
          final long value = parseDigits(text.substring(2), 4, 32);
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
    if (i == LONG_LITERAL) {
      if (StringUtil.endsWithChar(text, 'L') || StringUtil.endsWithChar(text, 'l')) {
        text = text.substring(0, textLength - 1);
      }
      try {
        if (text.startsWith(HEXPREFIX) || text.startsWith(HEXPREFIX2)) {
          return parseDigits(text.substring(2), 4, 64);
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
    if (i == FLOAT_LITERAL) {
      try {
        return Float.valueOf(text);
      }
      catch (NumberFormatException e) {
        return null;
      }
    }
    if (i == DOUBLE_LITERAL) {
      try {
        return Double.valueOf(text);
      }
      catch (NumberFormatException e) {
        return null;
      }
    }
    if (i == CHARACTER_LITERAL) {
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
    if (i == STRING_LITERAL) {
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
      return internedParseStringCharacters(text);
    }
    if (i == TRUE_KEYWORD) {
      return Boolean.TRUE;
    }
    if (i == FALSE_KEYWORD) {
      return Boolean.FALSE;
    }
    if (i == NULL_KEYWORD) {
      return null;
    }
    return null;
  }

  // convert text to number according to radix specified
  // if number is more than maxbits bits long, throws NumberFormatException
  private static long parseDigits(String text, int bitsInRadix, int maxBits) throws NumberFormatException {
    final int radix = 1 << bitsInRadix;
    int textLength = text.length();
    if (textLength == 0) {
      throw new NumberFormatException(text);
    }
    long integer = textLength == 1 ? 0 : Long.parseLong(text.substring(0, textLength - 1), radix);
    if ((integer & (-1L << (maxBits - bitsInRadix))) != 0) {
      throw new NumberFormatException(text);
    }
    int lastDigit = Character.digit(text.charAt(textLength - 1), radix);
    if (lastDigit == -1) {
      throw new NumberFormatException(text);
    }
    integer <<= bitsInRadix;
    integer |= lastDigit;
    return integer;
  }

  public String getParsingError() {
    final Object value = getValue();
    TreeElement firstChildNode = getFirstChildNode();
    String text = firstChildNode.getText();
    IElementType i = firstChildNode.getElementType();
    if (i == INTEGER_LITERAL) {
      text = text.toLowerCase();
      //literal 2147483648 may appear only as the operand of the unary negation operator -.
      if (!(text.equals(_2_IN_31)
            && getParent() instanceof PsiPrefixExpression
            && ((PsiPrefixExpression)getParent()).getOperationSign().getTokenType() == MINUS)) {
        if (text.equals(HEXPREFIX)) return JavaErrorMessages.message("hexadecimal.numbers.must.contain.at.least.one.hexadecimal.digit");
        if (value == null || text.equals(_2_IN_31)) {
          return JavaErrorMessages.message("integer.number.too.large");
        }
      }
    }
    else if (i == LONG_LITERAL) {
      text = text.toLowerCase();
      //literal 9223372036854775808L may appear only as the operand of the unary negation operator -.
      if (!(text.equals(_2_IN_63_L)
            && getParent() instanceof PsiPrefixExpression
            && ((PsiPrefixExpression)getParent()).getOperationSign().getTokenType() == MINUS)) {
        if (text.equals(HEXPREFIX) || text.equals(LHEX_PREFIX)) return JavaErrorMessages.message("hexadecimal.numbers.must.contain.at.least.one.hexadecimal.digit");
        if (value == null || text.equals(_2_IN_63_L)) {
          return JavaErrorMessages.message("long.number.too.large");
        }
      }
    }
    else if (i == FLOAT_LITERAL || i == DOUBLE_LITERAL) {
      if (value == null) {
        return JavaErrorMessages.message("malformed.floating.point.literal");
      }
    }
    else if (i == TRUE_KEYWORD || i == FALSE_KEYWORD || i == NULL_KEYWORD) {
      // TODO
    }
    else if (i == CHARACTER_LITERAL) {
      if (value == null) {
        if (!StringUtil.startsWithChar(text, '\'')) return null;
        if (StringUtil.endsWithChar(text, '\'')) {
          if (text.length() == 1) return JavaErrorMessages.message("illegal.line.end.in.character.literal");
          text = text.substring(1, text.length() - 1);
        }
        else {
          return JavaErrorMessages.message("illegal.line.end.in.character.literal");
        }
        StringBuilder chars = new StringBuilder();
        boolean success = parseStringCharacters(text, chars, null);
        if (!success) return JavaErrorMessages.message("illegal.escape.character.in.character.literal");
        if (chars.length() > 1) {
          return JavaErrorMessages.message("too.many.characters.in.character.literal");
        }
        else if (chars.length() == 0) return JavaErrorMessages.message("empty.character.literal");
      }
    }
    else if (i == STRING_LITERAL) {
      if (value == null) {
        for (final PsiElement element : getChildren()) {
          if (element instanceof OuterLanguageElement) {
            return null;
          }
        }

        if (!StringUtil.startsWithChar(text, '\"')) return null;
        if (StringUtil.endsWithChar(text, '\"')) {
          if (text.length() == 1) return JavaErrorMessages.message("illegal.line.end.in.string.literal");
          text = text.substring(1, text.length() - 1);
        }
        else {
          return JavaErrorMessages.message("illegal.line.end.in.string.literal");
        }
        StringBuilder chars = new StringBuilder();
        boolean success = parseStringCharacters(text, chars, null);
        if (!success) return JavaErrorMessages.message("illegal.escape.character.in.string.literal");
      }
    }

    if (value instanceof Float) {
      final Float number = (Float)value;
      if (number.isInfinite()) return JavaErrorMessages.message("floating.point.number.too.large");
      if (number.floatValue() == 0 && !isFPZero()) return JavaErrorMessages.message("floating.point.number.too.small");
    }
    if (value instanceof Double) {
      final Double number = (Double)value;
      if (number.isInfinite()) return JavaErrorMessages.message("floating.point.number.too.large");
      if (number.doubleValue() == 0 && !isFPZero()) return JavaErrorMessages.message("floating.point.number.too.small");
    }
    return null;
  }

  /**
   * @return true if floating point literal consists of zeros only
   */
  private boolean isFPZero() {
    String text = getFirstChildNode().getText();
    for(int i = 0; i < text.length(); i++){
      char c = text.charAt(i);
      if (Character.isDigit(c) && c != '0') return false;
      if (Character.toUpperCase(c) == 'E') break;
    }
    return true;
  }

  private static String internedParseStringCharacters(String chars) {
    StringBuilder outChars = new StringBuilder(chars.length());
    boolean success = parseStringCharacters(chars, outChars, null);
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
        case'7': {
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
        }
        break;

        case'u':
          if (index + 4 <= chars.length()) {
            try {
              int v = Integer.parseInt(chars.substring(index, index + 4), 16);
              //line separators are invalid here
              if (v == 0x000a || v == 0x000d) return false;
              c = chars.charAt(index);
              if (c == '+' || c == '-') return false;
              outChars.append((char)v);
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

  @NotNull
  public PsiReference[] getReferences() {
    return PsiReferenceService.getService().getContributedReferences(this);
  }

  @Nullable
  public List<Pair<PsiElement,TextRange>> getInjectedPsi() {
    if (!(getValue() instanceof String)) return null;

    return InjectedLanguageUtil.getInjectedPsiFiles(this);
  }

  public PsiLanguageInjectionHost updateText(@NotNull final String text) {
    TreeElement valueNode = getFirstChildNode();
    assert valueNode instanceof LeafElement;
    ((LeafElement)valueNode).replaceWithText(text);
    return this;
  }

  @NotNull
  public LiteralTextEscaper<PsiLiteralExpressionImpl> createLiteralTextEscaper() {
    return new StringLiteralEscaper(this);
  }

  public void processInjectedPsi(@NotNull InjectedPsiVisitor visitor) {
    InjectedLanguageUtil.enumerate(this, visitor);
  }
}


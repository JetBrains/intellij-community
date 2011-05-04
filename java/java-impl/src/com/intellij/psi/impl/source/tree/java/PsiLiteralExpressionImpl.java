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
import com.intellij.codeInsight.daemon.impl.analysis.IncreaseLanguageLevelFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.IntentionProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.impl.source.tree.injected.StringLiteralEscaper;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.text.LiteralFormatUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PsiLiteralExpressionImpl
       extends ExpressionPsiElement
       implements PsiLiteralExpression, PsiLanguageInjectionHost, ContributedReferenceHost, IntentionProvider {
  @NonNls private static final String QUOT = "&quot;";
  @NonNls private static final String HEX_PREFIX = "0x";
  @NonNls private static final String HEX_PREFIX2 = "0X";
  @NonNls private static final String LONG_HEX_EMPTY = "0xl";
  @NonNls private static final String BIN_PREFIX = "0b";
  @NonNls private static final String BIN_PREFIX2 = "0B";
  @NonNls private static final String LONG_BIN_EMPTY = "0bl";

  @NonNls private static final String _2_IN_63 = Long.toString(-1L << 63).substring(1);
  @NonNls private static final String _2_IN_31 = Long.toString(-1L << 31).substring(1);
  @NonNls private static final String _2_IN_63_L = _2_IN_63 + "l";

  private static final TokenSet INTEGER_LITERALS = TokenSet.create(JavaTokenType.INTEGER_LITERAL, JavaTokenType.LONG_LITERAL);
  private static final TokenSet REAL_LITERALS = TokenSet.create(JavaTokenType.FLOAT_LITERAL, JavaTokenType.DOUBLE_LITERAL);
  private static final TokenSet NUMERIC_LITERALS = TokenSet.orSet(INTEGER_LITERALS, REAL_LITERALS);

  public PsiLiteralExpressionImpl() {
    super(JavaElementType.LITERAL_EXPRESSION);
  }

  public PsiType getType() {
    final IElementType type = getFirstChildNode().getElementType();
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
      return PsiType.getJavaLangString(getManager(), getResolveScope());
    }
    if (type == JavaTokenType.TRUE_KEYWORD || type == JavaTokenType.FALSE_KEYWORD) {
      return PsiType.BOOLEAN;
    }
    if (type == JavaTokenType.NULL_KEYWORD) {
      return PsiType.NULL;
    }
    return null;
  }

  public String getCanonicalText() {
    final TreeElement literal = getFirstChildNode();
    final IElementType type = literal.getElementType();
    return NUMERIC_LITERALS.contains(type) ? LiteralFormatUtil.removeUnderscores(literal.getText()) : literal.getText();
  }

  public Object getValue() {
    final TreeElement literal = getFirstChildNode();
    final IElementType type = literal.getElementType();
    String text = getCanonicalText();
    final int textLength = text.length();

    if (type == JavaTokenType.INTEGER_LITERAL) {
      try {
        if (text.startsWith(HEX_PREFIX) || text.startsWith(HEX_PREFIX2)) {
          // should fit in 32 bits
          final long value = parseDigits(text.substring(2), 4, 32);
          return Integer.valueOf((int)value);
        }
        if (text.startsWith(BIN_PREFIX) || text.startsWith(BIN_PREFIX2)) {
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
        if (text.startsWith(HEX_PREFIX) || text.startsWith(HEX_PREFIX2)) {
          return parseDigits(text.substring(2), 4, 64);
        }
        if (text.startsWith(BIN_PREFIX) || text.startsWith(BIN_PREFIX2)) {
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
    if (type == JavaTokenType.TRUE_KEYWORD) {
      return Boolean.TRUE;
    }
    if (type == JavaTokenType.FALSE_KEYWORD) {
      return Boolean.FALSE;
    }

    return null;
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

  public String getParsingError() {
    final Object value = getValue();
    final TreeElement literal = getFirstChildNode();
    final IElementType type = literal.getElementType();
    String text = NUMERIC_LITERALS.contains(type) ? literal.getText().toLowerCase() : literal.getText();
    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(this);

    if (REAL_LITERALS.contains(type)) {
      if (text.startsWith(HEX_PREFIX) && !languageLevel.isAtLeast(LanguageLevel.JDK_1_5)) {
        return JavaErrorMessages.message("hex.FP.literals.not.supported");
      }
    }
    if (INTEGER_LITERALS.contains(type)) {
      if (text.startsWith(BIN_PREFIX) && !languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
        return JavaErrorMessages.message("binary.literals.not.supported");
      }
    }
    if (NUMERIC_LITERALS.contains(type)) {
      if (text.contains("_") && !languageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
        return JavaErrorMessages.message("underscores.in.literals.not.supported");
      }
    }

    if (type == JavaTokenType.INTEGER_LITERAL) {
      //literal 2147483648 may appear only as the operand of the unary negation operator -.
      if (!(text.equals(_2_IN_31)
            && getParent() instanceof PsiPrefixExpression
            && ((PsiPrefixExpression)getParent()).getOperationSign().getTokenType() == JavaTokenType.MINUS)) {
        if (text.equals(HEX_PREFIX)) {
          return JavaErrorMessages.message("hexadecimal.numbers.must.contain.at.least.one.hexadecimal.digit");
        }
        if (text.equals(BIN_PREFIX)) {
          return JavaErrorMessages.message("binary.numbers.must.contain.at.least.one.hexadecimal.digit");
        }
        if (value == null || text.equals(_2_IN_31)) {
          return JavaErrorMessages.message("integer.number.too.large");
        }
      }
    }
    else if (type == JavaTokenType.LONG_LITERAL) {
      //literal 9223372036854775808L may appear only as the operand of the unary negation operator -.
      if (!(text.equals(_2_IN_63_L)
            && getParent() instanceof PsiPrefixExpression
            && ((PsiPrefixExpression)getParent()).getOperationSign().getTokenType() == JavaTokenType.MINUS)) {
        if (text.equals(LONG_HEX_EMPTY)) {
          return JavaErrorMessages.message("hexadecimal.numbers.must.contain.at.least.one.hexadecimal.digit");
        }
        if (text.equals(LONG_BIN_EMPTY)) {
          return JavaErrorMessages.message("binary.numbers.must.contain.at.least.one.hexadecimal.digit");
        }
        if (value == null || text.equals(_2_IN_63_L)) {
          return JavaErrorMessages.message("long.number.too.large");
        }
      }
    }
    else if (type == JavaTokenType.FLOAT_LITERAL || type == JavaTokenType.DOUBLE_LITERAL) {
      if (value == null) {
        return JavaErrorMessages.message("malformed.floating.point.literal");
      }
    }
    else if (type == JavaTokenType.TRUE_KEYWORD || type == JavaTokenType.FALSE_KEYWORD || type == JavaTokenType.NULL_KEYWORD) {
      return null;
    }
    else if (type == JavaTokenType.CHARACTER_LITERAL) {
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
    else if (type == JavaTokenType.STRING_LITERAL) {
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
    else if (value instanceof Double) {
      final Double number = (Double)value;
      if (number.isInfinite()) return JavaErrorMessages.message("floating.point.number.too.large");
      if (number.doubleValue() == 0 && !isFPZero()) return JavaErrorMessages.message("floating.point.number.too.small");
    }

    return null;
  }

  @NotNull
  @Override
  public Collection<? extends IntentionAction> getIntentions() {
    final TreeElement literal = getFirstChildNode();
    final String text = literal.getText().toLowerCase();
    final IElementType type = literal.getElementType();
    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(this);

    if (REAL_LITERALS.contains(type)) {
      if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_5) && text.startsWith(HEX_PREFIX)) {
        return Arrays.asList(new IncreaseLanguageLevelFix(LanguageLevel.JDK_1_5));
      }
    }
    if (NUMERIC_LITERALS.contains(type)) {
      if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_7) && (text.startsWith(BIN_PREFIX) || text.contains("_"))) {
        return Arrays.asList(new IncreaseLanguageLevelFix(LanguageLevel.JDK_1_7));
      }
    }

    return Collections.emptyList();
  }

  /**
   * @return true if floating point literal consists of zeros only
   */
  private boolean isFPZero() {
    final String text = getFirstChildNode().getText();
    for(int i = 0; i < text.length(); i++){
      final char c = text.charAt(i);
      if (Character.isDigit(c) && c != '0') return false;
      if (Character.toUpperCase(c) == 'E') break;
    }
    return true;
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
    return new StringLiteralEscaper<PsiLiteralExpressionImpl>(this);
  }

  public void processInjectedPsi(@NotNull InjectedPsiVisitor visitor) {
    InjectedLanguageUtil.enumerate(this, visitor);
  }
}

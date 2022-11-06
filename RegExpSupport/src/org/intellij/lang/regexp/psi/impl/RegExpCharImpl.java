/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.UnicodeCharacterNames;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.jetbrains.annotations.NotNull;

public class RegExpCharImpl extends RegExpElementImpl implements RegExpChar {
    private static final TokenSet OCT_CHARS = TokenSet.create(RegExpTT.OCT_CHAR, RegExpTT.BAD_OCT_VALUE);
    private static final TokenSet HEX_CHARS = TokenSet.create(RegExpTT.HEX_CHAR, RegExpTT.BAD_HEX_VALUE);
    private static final TokenSet UNICODE_CHARS = TokenSet.create(RegExpTT.UNICODE_CHAR, StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN);

    public RegExpCharImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    @NotNull
    public Type getType() {
        final IElementType t = getNode().getFirstChildNode().getElementType();
        if (OCT_CHARS.contains(t)) {
            return Type.OCT;
        } else if (HEX_CHARS.contains(t)) {
            return Type.HEX;
        } else if (UNICODE_CHARS.contains(t)) {
            return Type.UNICODE;
        } else if (t == RegExpTT.NAMED_CHARACTER) {
            return Type.NAMED;
        } else if (t == RegExpTT.CTRL) {
            return Type.CONTROL;
        } else {
            return Type.CHAR;
        }
    }

    @Override
    public int getValue() {
        final ASTNode node = getNode();
        final IElementType type = node.getFirstChildNode().getElementType();
        if (type == RegExpTT.BAD_OCT_VALUE ||
            type == RegExpTT.BAD_HEX_VALUE ||
            type == RegExpTT.BAD_CHARACTER ||
            type == StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN) {
            return -1;
        }
        final String text = getUnescapedText();
        if (text.length() == 1 && (type == RegExpTT.CHARACTER ||  type == RegExpTT.CTRL_CHARACTER)) {
            return text.codePointAt(0);
        }
        else if (type == RegExpTT.UNICODE_CHAR) {
            final int i = text.indexOf('\\', 1);
            if (i >= 0) return Character.toCodePoint((char)unescapeChar(text.substring(0, i)), (char)unescapeChar(text.substring(i)));
        }
        return unescapeChar(text);
    }

    public static int unescapeChar(String s) {
        final int c = s.codePointAt(0);
        final int length = s.length();
        if (length == 1 || c != '\\') return -1;
        final int codePoint = s.codePointAt(1);
      return switch (codePoint) {
        case 'n' -> '\n';
        case 'r' -> '\r';
        case 't' -> '\t';
        case 'a' -> '\u0007'; // The alert (bell) character
        case 'e' -> '\u001b'; // The escape character
        case 'f' -> '\f'; // The form-feed character
        case 'b' -> '\b';
        case 'c' -> {
          if (length != 3) yield -1;
          yield s.codePointAt(2) ^ 64; // control character
        }
        case 'N' -> {
          if (length < 4 || s.charAt(2) != '{' || s.charAt(length - 1) != '}') {
            yield -1;
          }
          yield UnicodeCharacterNames.getCodePoint(s.substring(3, length - 1));
        }
        case 'x' -> {
          if (length <= 2) yield -1;
          if (s.charAt(2) == '{') {
            yield (s.charAt(length - 1) != '}') ? -1 : parseNumber(s, 3, 16);
          }
          if (length == 3) {
            yield parseNumber(s, 2, 16);
          }
          yield length == 4 ? parseNumber(s, 2, 16) : -1;
        }
        case 'u' -> {
          if (length <= 2) yield 'u';
          if (s.charAt(2) == '{') {
            yield (s.charAt(length - 1) != '}') ? -1 : parseNumber(s, 3, 16);
          }
          yield length != 6 ? -1 : parseNumber(s, 2, 16);
        }
        case '0', '1', '2', '3', '4', '5', '6', '7' -> parseNumber(s, 1, 8);
        default -> codePoint;
      };
    }

    private static int parseNumber(String s, int offset, int radix) {
        int sum = 0;
        int i = offset;
        for (; i < s.length(); i++) {
            final int digit = Character.digit(s.charAt(i), radix);
            if (digit < 0) { // '}' encountered
                break;
            }
            sum = sum * radix + digit;
            if (sum > Character.MAX_CODE_POINT) {
                return -1;
            }
        }
        if (i - offset <= 0) return -1; // no digits found
        return sum;
    }

    @Override
    public void accept(RegExpElementVisitor visitor) {
        visitor.visitRegExpChar(this);
    }
}

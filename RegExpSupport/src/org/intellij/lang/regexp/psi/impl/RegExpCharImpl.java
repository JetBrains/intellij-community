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
        final ASTNode child = getNode().getFirstChildNode();
        assert child != null;
        final IElementType t = child.getElementType();
        if (OCT_CHARS.contains(t)) {
            return Type.OCT;
        } else if (HEX_CHARS.contains(t)) {
            return Type.HEX;
        } else if (UNICODE_CHARS.contains(t)) {
            return Type.UNICODE;
        } else if (t == RegExpTT.NAMED_CHARACTER) {
            return Type.NAMED;
        } else {
            return Type.CHAR;
        }
    }

    @Override
    public int getValue() {
      final String s = getUnescapedText();
      if (s.equals("\\") && getType() == Type.CHAR) return '\\';
      return unescapeChar(s);
    }

    private static int unescapeChar(String s) {
        final int length = s.length();
        assert length > 0;

        int codePoint = s.codePointAt(0);
        if (codePoint != '\\') {
            return codePoint;
        }
        codePoint = s.codePointAt(1);
        switch (codePoint) {
            case 'n':
                return '\n';
            case 'r':
                return '\r';
            case 't':
                return '\t';
            case 'a':
                return '\u0007';
            case 'e':
                return '\u001b';
            case 'f':
                return '\f';
            case 'b':
                return '\b';
            case 'c':
                return (char)(codePoint ^ 64);
            case 'N':
                if (length < 4 || s.charAt(2) != '{' || s.charAt(length - 1) != '}') {
                    return -1;
                }
                final int value = UnicodeCharacterNames.getCodePoint(s.substring(3, length - 1));
                if (value == -1) {
                    return -1;
                }
                return value;
            case 'x':
                if (length <= 2) return -1;
                if (s.charAt(2) == '{') {
                    final char c = s.charAt(length - 1);
                    return (c != '}') ? -1 : parseNumber(s, 3, 16);
                }
                if (length == 3) {
                    return parseNumber(s, 2, 16);
                }
                return length == 4 ? parseNumber(s, 2, 16) : -1;
            case 'u':
                if (length <= 2) return -1;
                if (s.charAt(2) == '{') {
                    final char c = s.charAt(length - 1);
                    return (c != '}') ? -1 : parseNumber(s, 3, 16);
                }
                if (length != 6) {
                    return -1;
                }
                return parseNumber(s, 2, 16);
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
                return parseNumber(s, 1, 8);
            default:
                return codePoint;
        }
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

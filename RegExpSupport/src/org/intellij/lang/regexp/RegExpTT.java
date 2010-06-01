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
package org.intellij.lang.regexp;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.StringEscapesTokenTypes;

public interface RegExpTT {
    IElementType NUMBER = new RegExpElementType("NUMBER");
    IElementType NAME = new RegExpElementType("NAME");
    IElementType COMMA = new RegExpElementType("COMMA");

    // "\\Q"
    IElementType QUOTE_BEGIN = new RegExpElementType("QUOTE_BEGIN");
    // <QUOTE> "\\E"
    IElementType QUOTE_END = new RegExpElementType("QUOTE_END");

    // "\\" 0-9
    IElementType BACKREF = new RegExpElementType("BACKREF");

    IElementType LBRACE = new RegExpElementType("LBRACE");
    IElementType RBRACE = new RegExpElementType("RBRACE");

    IElementType CLASS_BEGIN = new RegExpElementType("CLASS_BEGIN");
    IElementType CLASS_END = new RegExpElementType("CLASS_END");
    IElementType ANDAND = new RegExpElementType("ANDAND");

    IElementType GROUP_BEGIN = new RegExpElementType("GROUP_BEGIN");
    IElementType GROUP_END = new RegExpElementType("GROUP_END");

    IElementType NON_CAPT_GROUP = new RegExpElementType("NON_CAPT_GROUP");
    IElementType POS_LOOKBEHIND = new RegExpElementType("POS_LOOKBEHIND");
    IElementType NEG_LOOKBEHIND = new RegExpElementType("NEG_LOOKBEHIND");
    IElementType POS_LOOKAHEAD = new RegExpElementType("POS_LOOKAHEAD");
    IElementType NEG_LOOKAHEAD = new RegExpElementType("NEG_LOOKAHEAD");
    IElementType SET_OPTIONS = new RegExpElementType("SET_OPTIONS");

    IElementType QUEST = new RegExpElementType("QUEST");
    IElementType STAR = new RegExpElementType("STAR");
    IElementType PLUS = new RegExpElementType("PLUS");
    IElementType COLON = new RegExpElementType("COLON");

    // "\\" ("b" | "B" | "A" | "z" | "Z" | "G")
    IElementType BOUNDARY = new RegExpElementType("BOUNDARY");
    // "^"
    IElementType CARET = new RegExpElementType("CARET");
    // "$"
    IElementType DOLLAR = new RegExpElementType("DOLLAR");

    IElementType DOT = new RegExpElementType("DOT");
    IElementType UNION = new RegExpElementType("UNION");

    // > in Python/Ruby named group
    IElementType GT = new RegExpElementType("GT");
    // ' in Ruby quoted named group
    IElementType QUOTE = new RegExpElementType("QUOTE");

    // "\b" | "\t" | "\f" | "\r" | "\n"
    IElementType CTRL_CHARACTER = new RegExpElementType("CTRL_CHARACTER");
    // "\\" ("t" | "n" | "r" | "f" | "a" | "e")
    IElementType ESC_CTRL_CHARACTER = new RegExpElementType("ESC_CTRL_CHARACTER");
    // "\\" ("." | "|" | "$" | "^" | "?" | "*" | "+" | "[" | "{" | "(" | ")")
    IElementType ESC_CHARACTER = new RegExpElementType("ESC_CHARACTER");
    // "\\" ("w" | "W" | "s" | "S" | "d" | "D")
    IElementType CHAR_CLASS = new RegExpElementType("CHAR_CLASS");
    // "\\u" XXXX
    IElementType UNICODE_CHAR = new RegExpElementType("UNICODE_CHAR");
    // "\\x" XX
    IElementType HEX_CHAR = new RegExpElementType("HEX_CHAR");
    // "\\0" OOO
    IElementType OCT_CHAR = new RegExpElementType("OCT_CHAR");
    // "\\c" x
    IElementType CTRL = new RegExpElementType("CTRL");
    // "\\p" | "\\P"
    IElementType PROPERTY = new RegExpElementType("PROPERTY");

    // e.g. "\\#" but also "\\q" which is not a valid escape actually
    IElementType REDUNDANT_ESCAPE = new RegExpElementType("REDUNDANT_ESCAPE");

    IElementType MINUS = new RegExpElementType("MINUS");
    IElementType CHARACTER = new RegExpElementType("CHARACTER");

    IElementType BAD_CHARACTER = new RegExpElementType("BAD_CHARACTER");
    IElementType BAD_OCT_VALUE = new RegExpElementType("BAD_OCT_VALUE");
    IElementType BAD_HEX_VALUE = new RegExpElementType("BAD_HEX_VALUE");

    IElementType COMMENT = new RegExpElementType("COMMENT");
    IElementType OPTIONS_ON = new RegExpElementType("OPTIONS_ON");
    IElementType OPTIONS_OFF = new RegExpElementType("OPTIONS_OFF");

    // (?P<name>...
    IElementType PYTHON_NAMED_GROUP = new RegExpElementType("PYTHON_NAMED_GROUP");
    // (?P=name)
    IElementType PYTHON_NAMED_GROUP_REF = new RegExpElementType("PYTHON_NAMED_GROUP_REF");
    // (?(id/name)yes-pattern|no-pattern)
    IElementType PYTHON_COND_REF = new RegExpElementType("PYTHON_COND_REF"); 
  
    // (?<name>...
    IElementType RUBY_NAMED_GROUP = new RegExpElementType("RUBY_NAMED_GROUP");

    // (?'name'...
    IElementType RUBY_QUOTED_NAMED_GROUP = new RegExpElementType("RUBY_QUOTED_NAMED_GROUP");

    TokenSet KEYWORDS = TokenSet.create(DOT, STAR, QUEST, PLUS);

    TokenSet CHARACTERS = TokenSet.create(CHARACTER,
            ESC_CTRL_CHARACTER,
            ESC_CHARACTER,
            CTRL_CHARACTER,
            UNICODE_CHAR,
            HEX_CHAR, BAD_HEX_VALUE,
            OCT_CHAR, BAD_OCT_VALUE,
            REDUNDANT_ESCAPE,
            MINUS,
            StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN,
            StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN);

    TokenSet SIMPLE_CLASSES = TokenSet.create(DOT, CHAR_CLASS);

    // caret is just a character in classes after the first position: [a^] matches "a" or "^"
    TokenSet CHARACTERS2 = TokenSet.orSet(CHARACTERS, SIMPLE_CLASSES, TokenSet.create(CARET, LBRACE));

    TokenSet QUANTIFIERS = TokenSet.create(QUEST, PLUS, STAR, LBRACE);

    TokenSet GROUPS = TokenSet.create(GROUP_BEGIN,
            NON_CAPT_GROUP,
            POS_LOOKAHEAD,
            NEG_LOOKAHEAD,
            POS_LOOKBEHIND,
            NEG_LOOKBEHIND);

    TokenSet BOUNDARIES = TokenSet.create(BOUNDARY, CARET, DOLLAR);
}

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

import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public interface RegExpTT {
    IElementType NUMBER = new RegExpElementType("NUMBER");
    IElementType NAME = new RegExpElementType("NAME");
    IElementType COMMA = new RegExpElementType("COMMA");

    /** "\\Q" */
    IElementType QUOTE_BEGIN = new RegExpElementType("QUOTE_BEGIN");
    /** <QUOTE> "\\E" */
    IElementType QUOTE_END = new RegExpElementType("QUOTE_END");

    /** "\\" 0-9 */
    IElementType BACKREF = new RegExpElementType("BACKREF");

    /** "{" */
    IElementType LBRACE = new RegExpElementType("LBRACE");
    /** "}" */
    IElementType RBRACE = new RegExpElementType("RBRACE");

    /** "[" */
    IElementType CLASS_BEGIN = new RegExpElementType("CLASS_BEGIN");
    /** "]" */
    IElementType CLASS_END = new RegExpElementType("CLASS_END");
    /** "&&" */
    IElementType ANDAND = new RegExpElementType("ANDAND");
    /** "[:" */
    IElementType BRACKET_EXPRESSION_BEGIN = new RegExpElementType("BRACKET_EXPRESSION_BEGIN");
    /** ":]" */
    IElementType BRACKET_EXPRESSION_END = new RegExpElementType("BRACKET_EXPRESSION_END");
    /** "[." */
    IElementType MYSQL_CHAR_BEGIN = new RegExpElementType("MYSQL_CHAR_BEGIN");
    /** ".]" */
    IElementType MYSQL_CHAR_END = new RegExpElementType("MYSQL_CHAR_END");
    /** "[=" */
    IElementType MYSQL_CHAR_EQ_BEGIN = new RegExpElementType("MYSQL_CHAR_EQ_BEGIN");
    /** "=]" */
    IElementType MYSQL_CHAR_EQ_END = new RegExpElementType("MYSQL_CHAR_EQ_END");

    /** "(" */
    IElementType GROUP_BEGIN = new RegExpElementType("GROUP_BEGIN");
    /** ")" */
    IElementType GROUP_END = new RegExpElementType("GROUP_END");

    /** "(?:" */
    IElementType NON_CAPT_GROUP = new RegExpElementType("NON_CAPT_GROUP");
    /** "(?>" */
    IElementType ATOMIC_GROUP = new RegExpElementType("ATOMIC_GROUP");
    /** "(?<=" */
    IElementType POS_LOOKBEHIND = new RegExpElementType("POS_LOOKBEHIND");
    /** "(?<!" */
    IElementType NEG_LOOKBEHIND = new RegExpElementType("NEG_LOOKBEHIND");
    /** "(?=" */
    IElementType POS_LOOKAHEAD = new RegExpElementType("POS_LOOKAHEAD");
    /** "(?!" */
    IElementType NEG_LOOKAHEAD = new RegExpElementType("NEG_LOOKAHEAD");
    /** "(?" */
    IElementType SET_OPTIONS = new RegExpElementType("SET_OPTIONS");

    IElementType QUEST = new RegExpElementType("QUEST");
    IElementType STAR = new RegExpElementType("STAR");
    IElementType PLUS = new RegExpElementType("PLUS");
    IElementType COLON = new RegExpElementType("COLON");

    /** "\\" ("b" | "B" | "A" | "z" | "Z" | "G" | "K") */
    IElementType BOUNDARY = new RegExpElementType("BOUNDARY");
    /** "^" */
    IElementType CARET = new RegExpElementType("CARET");
    /** "$" */
    IElementType DOLLAR = new RegExpElementType("DOLLAR");

    IElementType DOT = new RegExpElementType("DOT");
    /** "|" */
    IElementType UNION = new RegExpElementType("UNION");
    /**
     * "=" in some unicode properties, e.g.(\p{name=value})
     */
    IElementType EQ = new RegExpElementType("EQ");

    /** ">" in Python/Ruby named group */
    IElementType GT = new RegExpElementType("GT");
    /* "'" in Ruby quoted named group */
    IElementType QUOTE = new RegExpElementType("QUOTE");

    /** "\b" | "\t" | "\f" | "\r" | "\n" */
    IElementType CTRL_CHARACTER = new RegExpElementType("CTRL_CHARACTER");
    /** "\\" ("t" | "n" | "r" | "f" | "a" | "e") */
    IElementType ESC_CTRL_CHARACTER = new RegExpElementType("ESC_CTRL_CHARACTER");
    /** "\\" ("." | "|" | "$" | "^" | "?" | "*" | "+" | "[" | "{" | "(" | ")") */
    IElementType ESC_CHARACTER = new RegExpElementType("ESC_CHARACTER");
    /** "\\" ("w" | "W" | "s" | "S" | "d" | "D" | "v" | "V" | "h" | "H" | "X" | "R") */
    IElementType CHAR_CLASS = new RegExpElementType("CHAR_CLASS");
    /** "\\u" XXXX */
    IElementType UNICODE_CHAR = new RegExpElementType("UNICODE_CHAR");
    /** "\\x" XX */
    IElementType HEX_CHAR = new RegExpElementType("HEX_CHAR");
    /** "\\0" OOO */
    IElementType OCT_CHAR = new RegExpElementType("OCT_CHAR");
    /** "\\c" x */
    IElementType CTRL = new RegExpElementType("CTRL");
    /** "\\p" | "\\P" */
    IElementType PROPERTY = new RegExpElementType("PROPERTY");
    /** "\\N */
    IElementType NAMED_CHARACTER = new RegExpElementType("NAMED_CHARACTER");
    /** "L" | "M" | "Z" | "S" | "N" | "P" | "C" after a property escape */
    IElementType CATEGORY_SHORT_HAND = new RegExpElementType("CATEGORY_SHORT_HAND");

    /** e.g. "\\#" but also "\\q" which is not a valid escape actually */
    IElementType REDUNDANT_ESCAPE = new RegExpElementType("REDUNDANT_ESCAPE");

    IElementType MINUS = new RegExpElementType("MINUS");
    IElementType CHARACTER = new RegExpElementType("CHARACTER");

    IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;
    IElementType BAD_OCT_VALUE = new RegExpElementType("BAD_OCT_VALUE");
    IElementType BAD_HEX_VALUE = new RegExpElementType("BAD_HEX_VALUE");

    IElementType COMMENT = new RegExpElementType("COMMENT");
    IElementType OPTIONS_ON = new RegExpElementType("OPTIONS_ON");
    IElementType OPTIONS_OFF = new RegExpElementType("OPTIONS_OFF");

    /** (?P<name>... */
    IElementType PYTHON_NAMED_GROUP = new RegExpElementType("PYTHON_NAMED_GROUP");
    /** (?P>name) or (?&name) */
    IElementType PCRE_RECURSIVE_NAMED_GROUP_REF = new RegExpElementType("PCRE_RECURSIVE_NAMED_GROUP");
    /** (?group id) */
    IElementType PCRE_NUMBERED_GROUP_REF = new RegExpElementType("PCRE_NUMBERED_GROUP_REF");
    /** (?P=name) */
    IElementType PYTHON_NAMED_GROUP_REF = new RegExpElementType("PYTHON_NAMED_GROUP_REF");
    /** (?(id/name/lookaround)yes-pattern|no-pattern) */
    IElementType CONDITIONAL = new RegExpElementType("CONDITIONAL");
    /** (' */
    IElementType QUOTED_CONDITION_BEGIN = new RegExpElementType("QUOTED_CONDITION_BEGIN");
    IElementType QUOTED_CONDITION_END = new RegExpElementType("QUOTED_CONDITION_END");
    IElementType ANGLE_BRACKET_CONDITION_BEGIN = new RegExpElementType("ANGLE_BRACKET_CONDITION_BEGIN");
    IElementType ANGLE_BRACKET_CONDITION_END = new RegExpElementType("ANGLE_BRACKET_CONDITION_END");
    /** (?|regex) */
    IElementType PCRE_BRANCH_RESET = new RegExpElementType("PCRE_BRANCH_RESET");
    /** (?<name>... */
    IElementType RUBY_NAMED_GROUP = new RegExpElementType("RUBY_NAMED_GROUP");
    /** \k<name> */
    IElementType RUBY_NAMED_GROUP_REF = new RegExpElementType("RUBY_NAMED_GROUP_REF");
    /** \g<name> */
    IElementType RUBY_NAMED_GROUP_CALL = new RegExpElementType("RUBY_NAMED_GROUP_CALL");

    /** (?'name'... */
    IElementType RUBY_QUOTED_NAMED_GROUP = new RegExpElementType("RUBY_QUOTED_NAMED_GROUP");
    /** \k'name' */
    IElementType RUBY_QUOTED_NAMED_GROUP_REF = new RegExpElementType("RUBY_QUOTED_NAMED_GROUP_REF");
    /** \g'name' */
    IElementType RUBY_QUOTED_NAMED_GROUP_CALL = new RegExpElementType("RUBY_QUOTED_NAMED_GROUP_CALL");

    /** DEFINE
     * <a href="https://www.pcre.org/current/doc/html/pcre2pattern.html#subdefine">
     * */
    IElementType PCRE_DEFINE = new RegExpElementType("PCRE_DEFINE");

    /** VERSION[>]=n.m
     * <a href="https://www.pcre.org/current/doc/html/pcre2pattern.html#subdefine">
     * */
    IElementType PCRE_VERSION = new RegExpElementType("PCRE_VERSION");

    TokenSet PCRE_CONDITIONS = TokenSet.create(PCRE_DEFINE, PCRE_VERSION);

    TokenSet CHARACTERS = TokenSet.create(CHARACTER,
                                          ESC_CTRL_CHARACTER,
                                          ESC_CHARACTER,
                                          CTRL_CHARACTER,
                                          CTRL,
                                          UNICODE_CHAR,
                                          HEX_CHAR, BAD_HEX_VALUE,
                                          OCT_CHAR, BAD_OCT_VALUE,
                                          REDUNDANT_ESCAPE,
                                          MINUS,
                                          StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN,
                                          StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN);

    TokenSet QUANTIFIERS = TokenSet.create(QUEST, PLUS, STAR, LBRACE);

    TokenSet GROUPS = TokenSet.create(GROUP_BEGIN, NON_CAPT_GROUP, ATOMIC_GROUP, POS_LOOKAHEAD, NEG_LOOKAHEAD, POS_LOOKBEHIND, NEG_LOOKBEHIND, PCRE_BRANCH_RESET);
    TokenSet LOOKAROUND_GROUPS = TokenSet.create(POS_LOOKAHEAD, NEG_LOOKAHEAD, POS_LOOKBEHIND, NEG_LOOKBEHIND);

    TokenSet BOUNDARIES = TokenSet.create(BOUNDARY, CARET, DOLLAR);
}

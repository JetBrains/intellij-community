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

import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class RegExpHighlighter extends SyntaxHighlighterBase {
    private static final Map<IElementType, TextAttributesKey> keys1;
    private static final Map<IElementType, TextAttributesKey> keys2;

    static final TextAttributesKey META = TextAttributesKey.createTextAttributesKey(
      "REGEXP.META",
      DefaultLanguageHighlighterColors.KEYWORD
    );
  static final TextAttributesKey INVALID_CHARACTER_ESCAPE = TextAttributesKey.createTextAttributesKey(
    "REGEXP.INVALID_STRING_ESCAPE",
    DefaultLanguageHighlighterColors.INVALID_STRING_ESCAPE
  );
  static final TextAttributesKey BAD_CHARACTER = TextAttributesKey.createTextAttributesKey(
    "REGEXP.BAD_CHARACTER",
    HighlighterColors.BAD_CHARACTER
  );
  static final TextAttributesKey REDUNDANT_ESCAPE = TextAttributesKey.createTextAttributesKey(
    "REGEXP.REDUNDANT_ESCAPE",
    DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE
  );
  static final TextAttributesKey PARENTHS = TextAttributesKey.createTextAttributesKey(
    "REGEXP.PARENTHS",
    DefaultLanguageHighlighterColors.PARENTHESES
  );
  static final TextAttributesKey BRACES = TextAttributesKey.createTextAttributesKey(
    "REGEXP.BRACES",
    DefaultLanguageHighlighterColors.BRACES
  );
  static final TextAttributesKey BRACKETS = TextAttributesKey.createTextAttributesKey(
    "REGEXP.BRACKETS",
    DefaultLanguageHighlighterColors.BRACKETS
  );
  static final TextAttributesKey COMMA = TextAttributesKey.createTextAttributesKey(
    "REGEXP.COMMA",
    DefaultLanguageHighlighterColors.COMMA
  );
  static final TextAttributesKey ESC_CHARACTER = TextAttributesKey.createTextAttributesKey(
    "REGEXP.ESC_CHARACTER",
    DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE
  );
  static final TextAttributesKey CHAR_CLASS = TextAttributesKey.createTextAttributesKey(
    "REGEXP.CHAR_CLASS",
    DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE
  );
  static final TextAttributesKey QUOTE_CHARACTER = TextAttributesKey.createTextAttributesKey(
    "REGEXP.QUOTE_CHARACTER",
    DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE
  );
  static final TextAttributesKey COMMENT = TextAttributesKey.createTextAttributesKey(
    "REGEXP.COMMENT",
    DefaultLanguageHighlighterColors.LINE_COMMENT
  );

  private final Project myProject;
    private final ParserDefinition myParserDefinition;

    public RegExpHighlighter(Project project, ParserDefinition parserDefinition) {
        myProject = project;
        myParserDefinition = parserDefinition;
    }

    static {
        keys1 = new HashMap<>();
        keys2 = new HashMap<>();

        fillMap(keys1, RegExpTT.KEYWORDS, META);

        keys1.put(StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN, INVALID_CHARACTER_ESCAPE);
        keys1.put(StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN, INVALID_CHARACTER_ESCAPE);

        keys1.put(TokenType.BAD_CHARACTER, BAD_CHARACTER);
        keys1.put(RegExpTT.BAD_HEX_VALUE, INVALID_CHARACTER_ESCAPE);
        keys1.put(RegExpTT.BAD_OCT_VALUE, INVALID_CHARACTER_ESCAPE);

        keys1.put(RegExpTT.PROPERTY, CHAR_CLASS);

        keys1.put(RegExpTT.ESC_CHARACTER, ESC_CHARACTER);
        keys1.put(RegExpTT.UNICODE_CHAR, ESC_CHARACTER);
        keys1.put(RegExpTT.HEX_CHAR, ESC_CHARACTER);
        keys1.put(RegExpTT.OCT_CHAR, ESC_CHARACTER);
        keys1.put(RegExpTT.CHAR_CLASS, ESC_CHARACTER);
        keys1.put(RegExpTT.BOUNDARY, ESC_CHARACTER);
        keys1.put(RegExpTT.CTRL, ESC_CHARACTER);
        keys1.put(RegExpTT.ESC_CTRL_CHARACTER, ESC_CHARACTER);
        keys1.put(RegExpTT.CATEGORY_SHORT_HAND, ESC_CHARACTER);

        keys1.put(RegExpTT.REDUNDANT_ESCAPE, REDUNDANT_ESCAPE);

        keys1.put(RegExpTT.QUOTE_BEGIN, QUOTE_CHARACTER);
        keys1.put(RegExpTT.QUOTE_END, QUOTE_CHARACTER);

        keys1.put(RegExpTT.NON_CAPT_GROUP, PARENTHS);
        keys1.put(RegExpTT.POS_LOOKBEHIND, PARENTHS);
        keys1.put(RegExpTT.NEG_LOOKBEHIND, PARENTHS);
        keys1.put(RegExpTT.POS_LOOKAHEAD, PARENTHS);
        keys1.put(RegExpTT.NEG_LOOKAHEAD, PARENTHS);
        keys1.put(RegExpTT.SET_OPTIONS, PARENTHS);
        keys1.put(RegExpTT.PYTHON_NAMED_GROUP, PARENTHS);
        keys1.put(RegExpTT.PYTHON_NAMED_GROUP_REF, PARENTHS);
        keys1.put(RegExpTT.RUBY_NAMED_GROUP, PARENTHS);
        keys1.put(RegExpTT.RUBY_QUOTED_NAMED_GROUP, PARENTHS);
        keys1.put(RegExpTT.GROUP_BEGIN, PARENTHS);
        keys1.put(RegExpTT.GROUP_END, PARENTHS);

        keys1.put(RegExpTT.LBRACE, BRACES);
        keys1.put(RegExpTT.RBRACE, BRACES);

        keys1.put(RegExpTT.CLASS_BEGIN, BRACKETS);
        keys1.put(RegExpTT.CLASS_END, BRACKETS);

        keys1.put(RegExpTT.COMMA, COMMA);

        keys1.put(RegExpTT.COMMENT, COMMENT);
    }

    @NotNull
    public Lexer getHighlightingLexer() {
        return myParserDefinition.createLexer(myProject);
    }

    @NotNull
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        return pack(keys1.get(tokenType), keys2.get(tokenType));
    }
}

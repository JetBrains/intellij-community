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

package com.intellij.ide.highlighter.custom;

import com.intellij.ide.highlighter.custom.tokens.*;
import com.intellij.psi.CustomHighlighterTokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author dsl
 */
public final class CustomFileTypeLexer extends AbstractCustomLexer {
  public CustomFileTypeLexer(SyntaxTable table, boolean forHighlighting) {
    super(buildTokenParsers(table, forHighlighting));
  }

  public CustomFileTypeLexer(SyntaxTable table) {
    this(table, false);
  }

  private static List<TokenParser> buildTokenParsers(SyntaxTable table, boolean forHighlighting) {
    final LineCommentParser lineCommentParser = LineCommentParser.create(table.getLineComment());
    final MultilineCommentParser multilineCommentParser =
            MultilineCommentParser.create(table.getStartComment(), table.getEndComment());
    final NumberParser numberParser = new NumberParser(table.getNumPostfixChars(), table.isIgnoreCase());
    final HexNumberParser hexNumberParser = HexNumberParser.create(table.getHexPrefix());
    final KeywordParser keywordParser = new KeywordParser(
                    new Set[]{table.getKeywords1(), table.getKeywords2(), table.getKeywords3(), table.getKeywords4()},
                    table.isIgnoreCase());
    final IdentifierParser identifierParser = new IdentifierParser();

    final QuotedStringParser quotedStringParser = new QuotedStringParser("\"", CustomHighlighterTokenType.STRING, table.isHasStringEscapes());

    final QuotedStringParser quotedStringParser2 = new QuotedStringParser(
        "\'",
        forHighlighting ? CustomHighlighterTokenType.SINGLE_QUOTED_STRING:CustomHighlighterTokenType.STRING,
        table.isHasStringEscapes()
    );

    ArrayList<TokenParser> tokenParsers = new ArrayList<TokenParser>();
    tokenParsers.add(new WhitespaceParser());
    tokenParsers.add(quotedStringParser);
    tokenParsers.add(quotedStringParser2);
    if (lineCommentParser != null) {
      tokenParsers.add(lineCommentParser);
    }
    if (multilineCommentParser != null) {
      tokenParsers.add(multilineCommentParser);
    }
    tokenParsers.add(numberParser);
    tokenParsers.add(new PunctuationParser());
    if (hexNumberParser != null) {
      tokenParsers.add(hexNumberParser);
    }
    tokenParsers.add(keywordParser);
    tokenParsers.add(identifierParser);

    if (table.isHasBraces()) {
      tokenParsers.addAll(BraceTokenParser.BRACES);
    }

    if (table.isHasParens()) {
      tokenParsers.addAll(BraceTokenParser.PARENS);
    }

    if (table.isHasBrackets()) {
      tokenParsers.addAll(BraceTokenParser.BRACKETS);
    }

    return tokenParsers;
  }


}

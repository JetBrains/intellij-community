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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CustomHighlighterTokenType;

import java.util.ArrayList;
import java.util.List;

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
    final NumberParser numberParser = new NumberParser(table.getNumPostfixChars(), table.isIgnoreCase());
    final HexNumberParser hexNumberParser = HexNumberParser.create(table.getHexPrefix());
    
    final KeywordParser parser = table.getKeywordParser();
    final TokenParser keywordParser = new TokenParser() {
      @Override
      public boolean hasToken(int position) {
        return parser.hasToken(position, myBuffer, myTokenInfo);
      }
    };
    
    final IdentifierParser identifierParser = new IdentifierParser(parser);

    final QuotedStringParser quotedStringParser = new QuotedStringParser("\"", CustomHighlighterTokenType.STRING, table.isHasStringEscapes());

    final QuotedStringParser quotedStringParser2 = new QuotedStringParser(
        "\'",
        forHighlighting ? CustomHighlighterTokenType.SINGLE_QUOTED_STRING:CustomHighlighterTokenType.STRING,
        table.isHasStringEscapes()
    );

    ArrayList<TokenParser> tokenParsers = new ArrayList<>();
    tokenParsers.add(new WhitespaceParser());
    tokenParsers.add(new CommentParser(table));
    tokenParsers.add(keywordParser);
    tokenParsers.add(quotedStringParser);
    tokenParsers.add(quotedStringParser2);
    tokenParsers.add(new PunctuationParser());
    if (hexNumberParser != null) {
      tokenParsers.add(hexNumberParser);
    }
    tokenParsers.add(numberParser);
    tokenParsers.add(identifierParser);

    if (table.isHasBraces()) {
      tokenParsers.addAll(BraceTokenParser.getBraces());
    }

    if (table.isHasParens()) {
      tokenParsers.addAll(BraceTokenParser.getParens());
    }

    if (table.isHasBrackets()) {
      tokenParsers.addAll(BraceTokenParser.getBrackets());
    }

    return tokenParsers;
  }


  private static class CommentParser extends TokenParser {
    final LineCommentParser lineCommentParser;
    final MultilineCommentParser blockCommentParser;
    private final SyntaxTable myTable;

    CommentParser(SyntaxTable table) {
      myTable = table;
      lineCommentParser = StringUtil.isEmpty(myTable.getLineComment()) ? null : new LineCommentParser(myTable.getLineComment(), myTable.lineCommentOnlyAtStart);
      blockCommentParser = MultilineCommentParser.create(myTable.getStartComment(), myTable.getEndComment());
    }

    @Override
    public void setBuffer(CharSequence buffer, int startOffset, int endOffset) {
      super.setBuffer(buffer, startOffset, endOffset);
      if (lineCommentParser != null) lineCommentParser.setBuffer(buffer, startOffset, endOffset);
      if (blockCommentParser != null) blockCommentParser.setBuffer(buffer, startOffset, endOffset);
    }

    @Override
    public boolean hasToken(int position) {
      boolean hasBlock = blockCommentParser != null && blockCommentParser.hasToken(position);
      boolean hasLine = lineCommentParser != null && lineCommentParser.hasToken(position);
      if (hasBlock || hasLine) {
        TokenParser chosen = hasBlock && hasLine && myTable.getLineComment().startsWith(myTable.getStartComment()) ? lineCommentParser :
                             hasBlock ? blockCommentParser :
                             lineCommentParser;
        chosen.getTokenInfo(myTokenInfo);
        return true;
      }
      return false;
    }
  }
}

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

import com.intellij.lexer.LexerBase;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.IElementType;

/**
 * @author Yura Cangea
 * @version 1.0
 */
public class SyntaxTableLexer extends LexerBase {
  private final SyntaxTable table;

  private final PosBufferTokenizer tokenizer;

  private IElementType tokenType;
  private int tokenStart;
  private int tokenEnd;

  private final String lineComment;
  private final String startComment;
  private final String endComment;

  private CharSequence buffer;
  private int startOffset;
  private int endOffset;

  private boolean firstCall;

  public SyntaxTableLexer(SyntaxTable table) {
    this.table = table;

    tokenizer = new PosBufferTokenizer();
    tokenizer.setIgnoreCase(table.isIgnoreCase());

    tokenizer.setHexPrefix(table.getHexPrefix());
    tokenizer.setNumPostifxChars(table.getNumPostfixChars());

    tokenizer.ordinaryChar('/');
    tokenizer.ordinaryChar('.');
    tokenizer.quoteChar('"');
    tokenizer.quoteChar('\'');
    tokenizer.wordChars('_', '_');

    lineComment = table.getLineComment();
    startComment = table.getStartComment();
    endComment = table.getEndComment();
  }

  public void start(CharSequence buffer, int startOffset, int endOffset,
                    int initialState) {
    this.buffer = buffer;
    this.startOffset = startOffset;
    this.endOffset = endOffset;

    tokenType = null;

    tokenizer.start(buffer, startOffset, endOffset);

    firstCall = true;
  }

  private void parseToken() {
    String st = null;
    while (true) {
      int ttype = tokenizer.nextToken();

      switch (ttype) {
        case PosBufferTokenizer.TT_EOF:
          tokenStart = endOffset;
          tokenEnd = endOffset;
          tokenType = null;
          return;

        case PosBufferTokenizer.TT_WHITESPACE:
          tokenType = CustomHighlighterTokenType.WHITESPACE;
          break;

        case PosBufferTokenizer.TT_WORD:
          st = tokenizer.sval;
          if (table.getKeywords1().contains(st)) {
            tokenType = CustomHighlighterTokenType.KEYWORD_1;
          } else if (table.getKeywords2().contains(st)) {
            tokenType = CustomHighlighterTokenType.KEYWORD_2;
          } else if (table.getKeywords3().contains(st)) {
            tokenType = CustomHighlighterTokenType.KEYWORD_3;
          } else if (table.getKeywords4().contains(st)) {
            tokenType = CustomHighlighterTokenType.KEYWORD_4;
          } else {
            tokenType = CustomHighlighterTokenType.IDENTIFIER;
          }
          break;

        case PosBufferTokenizer.TT_NUMBER:
          tokenType = CustomHighlighterTokenType.NUMBER;
          break;

        case PosBufferTokenizer.TT_QUOTE:
          tokenType = CustomHighlighterTokenType.STRING;
          break;

        default:
          // Line comment
          if (lineComment != null && !"".equals(lineComment.trim())) {
            if (ttype == lineComment.charAt(0) && tokenizer.matchString(lineComment, 1)) {
              tokenType = CustomHighlighterTokenType.LINE_COMMENT;
              tokenStart = tokenizer.getPos() - lineComment.length();
              tokenizer.skipToEol();
              tokenEnd = tokenizer.getPos();
              return;
            }
          }

          // Block comment
          if (startComment != null && !"".equals(startComment.trim())) {
            if (ttype == startComment.charAt(0) && tokenizer.matchString(startComment, 1)) {
              tokenType = CustomHighlighterTokenType.MULTI_LINE_COMMENT;
              tokenStart = tokenizer.getPos() - startComment.length();
              tokenizer.skipToStr(endComment);
              tokenEnd = tokenizer.getPos();
              return;
            }
          }
          tokenType = CustomHighlighterTokenType.CHARACTER;
          break;
      }
      tokenStart = tokenizer.startOffset;
      tokenEnd = tokenizer.endOffset;
      return;
    }
  }


  public IElementType getTokenType() {
    if (firstCall) {
      advance();
    }
    return tokenType;
  }

  public int getTokenStart() {
    return tokenStart;
  }

  public int getTokenEnd() {
    return tokenEnd;
  }

  public void advance() {
    if (firstCall) {
      firstCall = false;
    }
    parseToken();
  }

  public CharSequence getBufferSequence() {
    return buffer;
  }

  public int getBufferEnd() {
    return endOffset;
  }

  public int getState() {
    // No state.
    return 0;
  }

}

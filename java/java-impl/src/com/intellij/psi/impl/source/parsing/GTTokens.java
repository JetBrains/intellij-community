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
package com.intellij.psi.impl.source.parsing;

import com.intellij.lang.ASTFactory;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;

/**
 *  @author dsl
 */
class GTTokens {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.GTTokens");
  static IElementType getTokenType(Lexer lexer) {
    if (lexer.getTokenType() != JavaTokenType.GT) return lexer.getTokenType();
    final LexerPosition originalPosition = lexer.getCurrentPosition();
    final int prevTokenEnd = lexer.getTokenEnd();
    lexer.advance();
    if (lexer.getTokenStart() != prevTokenEnd) {
      lexer.restore(originalPosition);
      return JavaTokenType.GT;
    }
    final IElementType resultType;
    IElementType i1 = lexer.getTokenType();
    if (i1 == JavaTokenType.EQ) {
      resultType = JavaTokenType.GE;
    }
    else if (i1 == JavaTokenType.GT) {
      final int prevTokenEnd2 = lexer.getTokenEnd();
      lexer.advance();
      if (lexer.getTokenStart() != prevTokenEnd2) {
        resultType = JavaTokenType.GTGT;
      }
      else {
        IElementType i = lexer.getTokenType();
        if (i == JavaTokenType.GT) {
          final int prevTokenEnd3 = lexer.getTokenEnd();
          lexer.advance();
          if (lexer.getTokenStart() != prevTokenEnd3 || lexer.getTokenType() != JavaTokenType.EQ) {
            resultType = JavaTokenType.GTGTGT;
          }
          else {
            resultType = JavaTokenType.GTGTGTEQ;
          }
        }
        else if (i == JavaTokenType.EQ) {
          resultType = JavaTokenType.GTGTEQ;
        }
        else {
          resultType = JavaTokenType.GTGT;
        }
      }
    }
    else {
      resultType = JavaTokenType.GT;
    }
    lexer.restore(originalPosition);
    return resultType;
  }

  static TreeElement createTokenElementAndAdvance(IElementType tokenType, Lexer lexer, CharTable table) {
    final TreeElement result;
    if (tokenType == JavaTokenType.GTGT || tokenType == JavaTokenType.GE) {
      result = mergeTokens(1, lexer, tokenType, table);
    }
    else if (tokenType == JavaTokenType.GTGTEQ || tokenType == JavaTokenType.GTGTGT) {
      result = mergeTokens(2, lexer, tokenType, table);
    }
    else if (tokenType == JavaTokenType.GTGTGTEQ) {
      result = mergeTokens(3, lexer, tokenType, table);
    }
    else {
      LOG.assertTrue(tokenType == lexer.getTokenType());
      result = ParseUtilBase.createTokenElement(lexer, table);
    }
    lexer.advance();
    return result;
  }

  static void advance(IElementType tokenType, Lexer lexer) {
    if(lexer.getTokenType() != JavaTokenType.GT){
      lexer.advance();
    }
    else {
      if (tokenType == JavaTokenType.GTGTGTEQ) {
        lexer.advance();
        lexer.advance();
        lexer.advance();
      }
      else if (tokenType == JavaTokenType.GTGTEQ || tokenType == JavaTokenType.GTGTGT) {
        lexer.advance();
        lexer.advance();
      }
      else if (tokenType == JavaTokenType.GTGT || tokenType == JavaTokenType.GE) {
        lexer.advance();
      }
      lexer.advance();
    }
  }

  private static TreeElement mergeTokens(int nTokens, Lexer lexer, IElementType tokenType, CharTable table) {
    final int tokenStart = lexer.getTokenStart();
    for (int i = 0; i < nTokens; i++) {
      lexer.advance();
    }
    final int tokenEnd = lexer.getTokenEnd();
    return ASTFactory.leaf(tokenType, table.intern(lexer.getBufferSequence(), tokenStart, tokenEnd));
  }
}

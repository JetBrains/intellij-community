/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.java.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class StatementParser {
  private static final boolean DEEP_PARSE_BLOCKS_IN_STATEMENTS = true;  // todo: reset after testing done

  private StatementParser() { }

  @Nullable
  public static PsiBuilder.Marker parseCodeBlock(final PsiBuilder builder) {
    if (builder.getTokenType() != JavaTokenType.LBRACE) return null;
    else if (DEEP_PARSE_BLOCKS_IN_STATEMENTS) return parseCodeBlockDeep(builder);

    final PsiBuilder.Marker codeBlock = builder.mark();
    builder.advanceLexer();

    int braceCount = 1;
    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == null) {
        break;
      }
      if (tokenType == JavaTokenType.LBRACE) {
        braceCount++;
      }
      else if (tokenType == JavaTokenType.RBRACE) {
        braceCount--;
      }
      builder.advanceLexer();

      if (braceCount == 0) {
        break;
      }
      else if (braceCount == 1 && (tokenType == JavaTokenType.SEMICOLON || tokenType == JavaTokenType.RBRACE)) {
        final PsiBuilder.Marker position = builder.mark();
        final List<IElementType> list = new SmartList<IElementType>();
        while (true) {
          final IElementType type = builder.getTokenType();
          if (ElementType.PRIMITIVE_TYPE_BIT_SET.contains(type) || ElementType.MODIFIER_BIT_SET.contains(type) ||
              type == JavaTokenType.IDENTIFIER || type == JavaTokenType.LT || type == JavaTokenType.GT ||
              type == JavaTokenType.GTGT || type == JavaTokenType.GTGTGT || type == JavaTokenType.COMMA ||
              type == JavaTokenType.DOT || type == JavaTokenType.EXTENDS_KEYWORD || type == JavaTokenType.IMPLEMENTS_KEYWORD) {
            list.add(type);
            builder.advanceLexer();
          } else {
            break;
          }
        }
        if (builder.getTokenType() == JavaTokenType.LPARENTH && list.size() >= 2) {
          final IElementType last = list.get(list.size() - 1);
          final IElementType prevLast = list.get(list.size() - 2);
          if (last == JavaTokenType.IDENTIFIER &&
              (prevLast == JavaTokenType.IDENTIFIER || ElementType.PRIMITIVE_TYPE_BIT_SET.contains(prevLast))) {
            position.rollbackTo();
            break;
          }
        }
        position.drop();
      }
    }

    codeBlock.collapse(JavaElementType.CODE_BLOCK);
    return codeBlock;
  }

  @Nullable
  public static PsiBuilder.Marker parseCodeBlockDeep(final PsiBuilder builder) {
    if (builder.getTokenType() != JavaTokenType.LBRACE) return null;

    final PsiBuilder.Marker codeBlock = builder.mark();
    builder.advanceLexer();

    // temp
    if (builder.getTokenType() == JavaTokenType.RBRACE || builder.eof()) {
      builder.advanceLexer();
      codeBlock.done(JavaElementType.CODE_BLOCK);
      return codeBlock;
    }

    // todo: implement
    codeBlock.drop();
    throw new UnsupportedOperationException(builder.toString());
  }
}

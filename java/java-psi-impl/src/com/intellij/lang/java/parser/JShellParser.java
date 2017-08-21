/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JShellElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;

/**
 * @author Eugene Zhuravlev
 * Date: 21-Jun-17
 */
public class JShellParser extends JavaParser {
  public static final JShellParser INSTANCE = new JShellParser();

  private static final HashSet<IElementType> TOP_LEVEL_DECLARATIONS = new HashSet<>(Arrays.asList(
    JavaElementType.FIELD, JavaElementType.METHOD, JavaElementType.CLASS
  ));
  private static final Condition<IElementType> IMPORT_PARSED_CONDITION = tokenType -> JavaElementType.IMPORT_STATEMENT.equals(tokenType);
  private static final Condition<IElementType> EXPRESSION_PARSED_CONDITION = type -> type != JavaElementType.REFERENCE_EXPRESSION;
  private static final Condition<IElementType> STATEMENTS_PARSED_CONDITION = tokenType-> !JavaElementType.DECLARATION_STATEMENT.equals(tokenType) &&
                                                                                         !JavaElementType.EXPRESSION_STATEMENT.equals(tokenType);
  private static final Condition<IElementType> DECLARATION_PARSED_CONDITION = tokenType -> TOP_LEVEL_DECLARATIONS.contains(tokenType);

  private final FileParser myJShellFileParser = new FileParser(JShellParser.this) {
    private final TokenSet IMPORT_PARSING_STOP_LIST = TokenSet.orSet(
      IMPORT_LIST_STOPPER_SET,
      TokenSet.orSet(
        ElementType.MODIFIER_BIT_SET,
        ElementType.JAVA_COMMENT_BIT_SET,
        ElementType.EXPRESSION_BIT_SET,
        ElementType.JAVA_STATEMENT_BIT_SET,
        TokenSet.create(JShellElementType.ROOT_CLASS, JavaTokenType.IDENTIFIER)
      )
    );

    @Override
    public void parse(@NotNull PsiBuilder builder) {
      parseImportList(builder, b -> IMPORT_PARSING_STOP_LIST.contains(b.getTokenType()));

      final PsiBuilder.Marker rootClass = builder.mark();
      try {
        while (!builder.eof()) {
          PsiBuilder.Marker wrapper = builder.mark();
          IElementType wrapperType = null;

          PsiBuilder.Marker marker = parseImportStatement(builder);
          if (isParsed(marker, builder, IMPORT_PARSED_CONDITION)) {
            wrapperType = JShellElementType.IMPORT_HOLDER;
          }
          else {
            revert(marker);
            marker = getExpressionParser().parse(builder);
            // in case of reference expression try other options and only if they fail, parse as expression again
            if (isParsed(marker, builder, EXPRESSION_PARSED_CONDITION)) {
              wrapperType = JShellElementType.STATEMENTS_HOLDER;
            }
            else {
              revert(marker);
              marker = getStatementParser().parseStatement(builder);
              if (isParsed(marker, builder, STATEMENTS_PARSED_CONDITION)) {
                wrapperType = JShellElementType.STATEMENTS_HOLDER;
              }
              else {
                revert(marker);
                marker = getDeclarationParser().parse(builder, DeclarationParser.Context.CLASS);
                if (isParsed(marker, builder, DECLARATION_PARSED_CONDITION)) {
                  wrapper.drop(); // don't need wrapper for top-level declaration
                  wrapper = null;
                }
                else {
                  revert(marker);
                  marker = getExpressionParser().parse(builder);
                  wrapperType = marker != null? JShellElementType.STATEMENTS_HOLDER : null;
                }
              }
            }
          }

          if (marker == null) {
            if (wrapper != null) {
              wrapper.drop();
            }
            break;
          }

          if (wrapper != null) {
            wrapper.done(wrapperType);
          }
        }

        if (!builder.eof()) {
          builder.mark().error(JavaErrorMessages.message("unexpected.token"));
          while (!builder.eof()) {
            builder.advanceLexer();
          }
        }
      }
      finally {
        rootClass.done(JShellElementType.ROOT_CLASS);
      }
    }
  };

  private static boolean isParsed(@Nullable PsiBuilder.Marker parsedMarker, PsiBuilder builder, final Condition<IElementType> cond) {
    if (parsedMarker == null) {
      return false;
    }
    final LighterASTNode lastDone = builder.getLatestDoneMarker();
    if (lastDone == null) {
      return false;
    }
    return cond.value(lastDone.getTokenType());
  }

  private static void revert(PsiBuilder.Marker parsedMarker) {
    if (parsedMarker != null) {
      parsedMarker.rollbackTo();
    }
  }

  @NotNull
  @Override
  public FileParser getFileParser() {
    return myJShellFileParser;
  }
}

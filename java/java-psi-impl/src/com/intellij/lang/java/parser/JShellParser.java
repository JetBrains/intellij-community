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
import com.intellij.psi.impl.source.tree.JShellElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 * Date: 21-Jun-17
 */
public class JShellParser extends JavaParser {
  public static final JShellParser INSTANCE = new JShellParser();

  private static final Set<IElementType> IMPORT = Collections.singleton(JavaElementType.IMPORT_STATEMENT);
  private static final HashSet<IElementType> TOP_LEVEL_DECLARATIONS = new HashSet<>(Arrays.asList(
    JavaElementType.FIELD, JavaElementType.METHOD, JavaElementType.CLASS
  ));

  private final FileParser myJShellFileParser = new FileParser(JShellParser.this) {
    @Override
    public void parse(@NotNull PsiBuilder builder) {
      while (!builder.eof()) {
        PsiBuilder.Marker wrapper = builder.mark();
        IElementType wrapperType = null;

        PsiBuilder.Marker marker = parseImportStatement(builder);
        if (isParsed(marker, builder, tokenType -> IMPORT.contains(tokenType))) {
          wrapperType = JShellElementType.IMPORT_HOLDER;
        }
        else {
          revert(marker);
          marker = getExpressionParser().parse(builder);
          if (marker != null) {
            wrapperType = JShellElementType.STATEMENTS_HOLDER;
          }
          else {
            marker = getStatementParser().parseStatement(builder);
            if (isParsed(marker, builder, tokenType-> !JavaElementType.DECLARATION_STATEMENT.equals(tokenType))) {
              wrapperType = JShellElementType.STATEMENTS_HOLDER;
            }
            else {
              revert(marker);
              marker = getDeclarationParser().parse(builder, DeclarationParser.Context.CLASS);
              if (isParsed(marker, builder, tokenType -> TOP_LEVEL_DECLARATIONS.contains(tokenType))) {
                wrapper.drop(); // don't need wrapper for top-level declaration
                wrapper = null;
              }
              else {
                revert(marker);
                marker = null;
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

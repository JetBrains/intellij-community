/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.AbstractBundle;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.JavaParserUtil.*;

public class FileParser {
  private static final TokenSet IMPORT_LIST_STOPPER_SET = TokenSet.orSet(
    ElementType.MODIFIER_BIT_SET,
    TokenSet.create(JavaTokenType.CLASS_KEYWORD, JavaTokenType.INTERFACE_KEYWORD, JavaTokenType.ENUM_KEYWORD, JavaTokenType.AT));

  private final JavaParser myParser;

  public FileParser(@NotNull final JavaParser javaParser) {
    myParser = javaParser;
  }

  public void parse(final PsiBuilder builder) {
    parseFile(builder, IMPORT_LIST_STOPPER_SET, JavaErrorMessages.INSTANCE, "expected.class.or.interface");
  }

  private static String error(@NotNull AbstractBundle bundle, @NotNull String errorMessageKey) {
    return bundle.getMessage(errorMessageKey);
  }

  public void parseFile(@NotNull final PsiBuilder builder,
                        @NotNull final TokenSet importListStoppers,
                        @NotNull final AbstractBundle bundle,
                        @NotNull final String errorMessageKey) {
    parsePackageStatement(builder);

    Pair<PsiBuilder.Marker, Boolean> impListInfo = parseImportList(builder, importListStoppers);

    Boolean firstDeclarationOk = null;
    PsiBuilder.Marker firstDeclaration = null;
    PsiBuilder.Marker invalidElements = null;
    while (!builder.eof()) {
      if (builder.getTokenType() == JavaTokenType.SEMICOLON) {
        builder.advanceLexer();
        continue;
      }

      final PsiBuilder.Marker declaration = parseInitial(builder);
      if (declaration != null) {
        if (invalidElements != null) {
          invalidElements.errorBefore(error(bundle, errorMessageKey), declaration);
          invalidElements = null;
        }
        if (firstDeclarationOk == null) {
          firstDeclarationOk = exprType(declaration) != JavaElementType.MODIFIER_LIST;
          if (firstDeclarationOk) {
            firstDeclaration = declaration;
          }
        }
        continue;
      }

      if (invalidElements == null) {
        invalidElements = builder.mark();
      }
      builder.advanceLexer();
      if (firstDeclarationOk == null) firstDeclarationOk = false;
    }

    if (invalidElements != null) {
      invalidElements.error(error(bundle, errorMessageKey));
    }

    if (impListInfo.second && firstDeclarationOk == Boolean.TRUE) {
      impListInfo.first.setCustomEdgeTokenBinders(PRECEDING_COMMENT_BINDER, null);  // pass comments behind fake import list
      firstDeclaration.setCustomEdgeTokenBinders(SPECIAL_PRECEDING_COMMENT_BINDER, null);
    }
  }

  @Nullable
  protected PsiBuilder.Marker parseInitial(PsiBuilder builder) {
    return myParser.getDeclarationParser().parse(builder, DeclarationParser.Context.FILE);
  }

  @Nullable
  public PsiBuilder.Marker parsePackageStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();

    if (!expect(builder, JavaTokenType.PACKAGE_KEYWORD)) {
      final PsiBuilder.Marker modList = builder.mark();
      myParser.getDeclarationParser().parseAnnotations(builder);
      done(modList, JavaElementType.MODIFIER_LIST);
      if (!expect(builder, JavaTokenType.PACKAGE_KEYWORD)) {
        statement.rollbackTo();
        return null;
      }
    }

    final PsiBuilder.Marker ref = myParser.getReferenceParser().parseJavaCodeReference(builder, true, false, false, false);
    if (ref == null) {
      statement.error(JavaErrorMessages.message("expected.class.or.interface"));
      return null;
    }

    semicolon(builder);

    done(statement, JavaElementType.PACKAGE_STATEMENT);
    return statement;
  }

  @NotNull
  public Pair<PsiBuilder.Marker, Boolean> parseImportList(final PsiBuilder builder, final TokenSet stoppers) {
    PsiBuilder.Marker list = builder.mark();

    IElementType tokenType = builder.getTokenType();
    boolean isEmpty = tokenType != JavaTokenType.IMPORT_KEYWORD && tokenType != JavaTokenType.SEMICOLON;
    if (!isEmpty) {
      PsiBuilder.Marker invalidElements = null;
      while (!builder.eof()) {
        tokenType = builder.getTokenType();
        if (stoppers.contains(tokenType)) {
          break;
        }
        else if (tokenType == JavaTokenType.SEMICOLON) {
          builder.advanceLexer();
          continue;
        }

        final PsiBuilder.Marker statement = parseImportStatement(builder);
        if (statement != null) {
          if (invalidElements != null) {
            invalidElements.errorBefore(JavaErrorMessages.message("unexpected.token"), statement);
            invalidElements = null;
          }
          continue;
        }

        if (invalidElements == null) {
          invalidElements = builder.mark();
        }
        builder.advanceLexer();
      }

      if (invalidElements != null) {
        invalidElements.error(JavaErrorMessages.message("unexpected.token"));
      }
    }

    done(list, JavaElementType.IMPORT_LIST);
    return Pair.create(list, isEmpty);
  }

  @Nullable
  private PsiBuilder.Marker parseImportStatement(final PsiBuilder builder) {
    if (builder.getTokenType() != JavaTokenType.IMPORT_KEYWORD) return null;

    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    final boolean isStatic = expect(builder, JavaTokenType.STATIC_KEYWORD);
    final IElementType type = isStatic ? JavaElementType.IMPORT_STATIC_STATEMENT : JavaElementType.IMPORT_STATEMENT;

    final boolean isOk = myParser.getReferenceParser().parseImportCodeReference(builder, isStatic);
    if (isOk) {
      semicolon(builder);
    }

    done(statement, type);
    return statement;
  }
}

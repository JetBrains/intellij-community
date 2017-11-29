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

import com.intellij.AbstractBundle;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.JavaParserUtil.*;

public class FileParser {
  protected static final TokenSet IMPORT_LIST_STOPPER_SET = TokenSet.orSet(
    ElementType.MODIFIER_BIT_SET,
    TokenSet.create(JavaTokenType.CLASS_KEYWORD, JavaTokenType.INTERFACE_KEYWORD, JavaTokenType.ENUM_KEYWORD, JavaTokenType.AT));

  private final JavaParser myParser;

  public FileParser(@NotNull JavaParser javaParser) {
    myParser = javaParser;
  }

  public void parse(@NotNull PsiBuilder builder) {
    parseFile(builder, FileParser::stopImportListParsing, JavaErrorMessages.INSTANCE, "expected.class.or.interface");
  }

  public void parseFile(@NotNull PsiBuilder builder,
                        @NotNull Predicate<PsiBuilder> importListStopper,
                        @NotNull AbstractBundle bundle,
                        @NotNull String errorMessageKey) {
    parsePackageStatement(builder);

    Pair<PsiBuilder.Marker, Boolean> impListInfo = parseImportList(builder, importListStopper);  // (importList, isEmpty)
    Boolean firstDeclarationOk = null;
    PsiBuilder.Marker firstDeclaration = null;

    PsiBuilder.Marker invalidElements = null;
    while (!builder.eof()) {
      if (builder.getTokenType() == JavaTokenType.SEMICOLON) {
        builder.advanceLexer();
        continue;
      }

      PsiBuilder.Marker declaration = myParser.getModuleParser().parse(builder);
      if (declaration == null) declaration = parseInitial(builder);
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

  private static boolean stopImportListParsing(PsiBuilder b) {
    IElementType type = b.getTokenType();
    if (IMPORT_LIST_STOPPER_SET.contains(type)) return true;
    if (type == JavaTokenType.IDENTIFIER) {
      String text = b.getTokenText();
      if (PsiKeyword.OPEN.equals(text) || PsiKeyword.MODULE.equals(text)) return true;
    }
    return false;
  }

  @Nullable
  protected PsiBuilder.Marker parseInitial(PsiBuilder builder) {
    return myParser.getDeclarationParser().parse(builder, DeclarationParser.Context.FILE);
  }

  private void parsePackageStatement(PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();

    if (!expect(builder, JavaTokenType.PACKAGE_KEYWORD)) {
      final PsiBuilder.Marker modList = builder.mark();
      myParser.getDeclarationParser().parseAnnotations(builder);
      done(modList, JavaElementType.MODIFIER_LIST);
      if (!expect(builder, JavaTokenType.PACKAGE_KEYWORD)) {
        statement.rollbackTo();
        return;
      }
    }

    final PsiBuilder.Marker ref = myParser.getReferenceParser().parseJavaCodeReference(builder, true, false, false, false);
    if (ref == null) {
      statement.error(JavaErrorMessages.message("expected.class.or.interface"));
      return;
    }

    semicolon(builder);

    done(statement, JavaElementType.PACKAGE_STATEMENT);
  }

  @NotNull
  protected Pair<PsiBuilder.Marker, Boolean> parseImportList(PsiBuilder builder, Predicate<PsiBuilder> stopper) {
    PsiBuilder.Marker list = builder.mark();

    boolean isEmpty = true;
    PsiBuilder.Marker invalidElements = null;
    while (!builder.eof()) {
      if (stopper.test(builder)) {
        break;
      }
      else if (builder.getTokenType() == JavaTokenType.SEMICOLON) {
        builder.advanceLexer();
        continue;
      }

      final PsiBuilder.Marker statement = parseImportStatement(builder);
      if (statement != null) {
        isEmpty = false;
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

    if (isEmpty) {
      PsiBuilder.Marker precede = list.precede();
      list.rollbackTo();
      list = precede;
    }

    done(list, JavaElementType.IMPORT_LIST);
    return Pair.create(list, isEmpty);
  }

  @Nullable
  protected PsiBuilder.Marker parseImportStatement(PsiBuilder builder) {
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

  private static String error(@NotNull AbstractBundle bundle, @NotNull String errorMessageKey) {
    return bundle.getMessage(errorMessageKey);
  }
}
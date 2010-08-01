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

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.JavaParserUtil.semicolon;


public class FileParser {
  private static final TokenSet IMPORT_LIST_STOPPER_SET = TokenSet.orSet(
    ElementType.MODIFIER_BIT_SET,
    TokenSet.create(JavaTokenType.CLASS_KEYWORD, JavaTokenType.INTERFACE_KEYWORD, JavaTokenType.ENUM_KEYWORD, JavaTokenType.AT));

  private FileParser() { }

  public static void parse(final PsiBuilder builder) {
    parsePackageStatement(builder);

    parseImportList(builder);

    PsiBuilder.Marker invalidElements = null;
    while (!builder.eof()) {
      if (builder.getTokenType() == JavaTokenType.SEMICOLON) {
        if (invalidElements != null) {
          invalidElements.error(JavaErrorMessages.message("expected.class.or.interface"));
          invalidElements = null;
        }
        builder.advanceLexer();
        continue;
      }

      final PsiBuilder.Marker declaration = DeclarationParser.parse(builder, DeclarationParser.Context.FILE);
      if (declaration != null) {
        if (invalidElements != null) {
          invalidElements.errorBefore(JavaErrorMessages.message("expected.class.or.interface"), declaration);
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
      invalidElements.error(JavaErrorMessages.message("expected.class.or.interface"));
    }
  }

  @Nullable
  private static PsiBuilder.Marker parsePackageStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();

    if (!expect(builder, JavaTokenType.PACKAGE_KEYWORD)) {
      final PsiBuilder.Marker modList = builder.mark();
      DeclarationParser.parseAnnotations(builder);
      modList.done(JavaElementType.MODIFIER_LIST);
      if (!expect(builder, JavaTokenType.PACKAGE_KEYWORD)) {
        statement.rollbackTo();
        return null;
      }
    }

    final PsiBuilder.Marker ref = ReferenceParser.parseJavaCodeReference(builder, true, false, false);
    if (ref == null) {
      statement.rollbackTo();
      return null;
    }

    semicolon(builder);

    statement.done(JavaElementType.PACKAGE_STATEMENT);
    return statement;
  }

  @NotNull
  private static PsiBuilder.Marker parseImportList(final PsiBuilder builder) {
    final PsiBuilder.Marker list = builder.mark();

    if (builder.getTokenType() == JavaTokenType.IMPORT_KEYWORD) {
      PsiBuilder.Marker invalidElements = null;
      while (!builder.eof()) {
        if (IMPORT_LIST_STOPPER_SET.contains(builder.getTokenType())) break;

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

    list.done(JavaElementType.IMPORT_LIST);
    return list;
  }

  @Nullable
  private static PsiBuilder.Marker parseImportStatement(final PsiBuilder builder) {
    if (builder.getTokenType() != JavaTokenType.IMPORT_KEYWORD) return null;

    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    final boolean isStatic = expect(builder, JavaTokenType.STATIC_KEYWORD);
    final IElementType type = isStatic ? JavaElementType.IMPORT_STATIC_STATEMENT : JavaElementType.IMPORT_STATEMENT;

    final boolean isOk = ReferenceParser.parseImportCodeReference(builder, isStatic);
    if (isOk) {
      semicolon(builder);
    }

    statement.done(type);
    return statement;
  }
}

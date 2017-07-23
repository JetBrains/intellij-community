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
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.JavaParserUtil.error;
import static com.intellij.lang.java.parser.JavaParserUtil.semicolon;

public class ModuleParser {
  private static final Set<String> STATEMENT_KEYWORDS =
    ContainerUtil.newHashSet(PsiKeyword.REQUIRES, PsiKeyword.EXPORTS, PsiKeyword.USES, PsiKeyword.PROVIDES);

  private final JavaParser myParser;

  public ModuleParser(@NotNull JavaParser parser) {
    myParser = parser;
  }

  @Nullable
  public PsiBuilder.Marker parse(@NotNull PsiBuilder builder) {
    PsiBuilder.Marker module = builder.mark();

    PsiBuilder.Marker firstAnnotation = myParser.getDeclarationParser().parseAnnotations(builder);

    IElementType type = builder.getTokenType();
    String text = type == JavaTokenType.IDENTIFIER ? builder.getTokenText() : null;
    if (!(PsiKeyword.OPEN.equals(text) || PsiKeyword.MODULE.equals(text))) {
      module.rollbackTo();
      return null;
    }

    PsiBuilder.Marker modifierList = firstAnnotation != null ? firstAnnotation.precede() : builder.mark();
    if (PsiKeyword.OPEN.equals(text)) {
      mapAndAdvance(builder, JavaTokenType.OPEN_KEYWORD);
      text = builder.getTokenText();
    }
    JavaParserUtil.done(modifierList, JavaElementType.MODIFIER_LIST);

    if (PsiKeyword.MODULE.equals(text)) {
      mapAndAdvance(builder, JavaTokenType.MODULE_KEYWORD);
    }
    else {
      module.drop();
      parseExtras(builder, JavaErrorMessages.message("expected.module.declaration"));
      return module;
    }

    if (parseName(builder) == null) {
      module.drop();
      if (builder.getTokenType() != null) {
        parseExtras(builder, JavaErrorMessages.message("expected.module.declaration"));
      }
      else {
        error(builder, JavaErrorMessages.message("expected.identifier"));
      }
      return module;
    }

    if (!expect(builder, JavaTokenType.LBRACE)) {
      if (builder.getTokenType() != null) {
        parseExtras(builder, JavaErrorMessages.message("expected.module.declaration"));
      }
      else {
        error(builder, JavaErrorMessages.message("expected.lbrace"));
      }
    }
    else {
      parseModuleContent(builder);
    }

    JavaParserUtil.done(module, JavaElementType.MODULE);

    if (builder.getTokenType() != null) {
      parseExtras(builder, JavaErrorMessages.message("unexpected.tokens"));
    }

    return module;
  }

  private static PsiBuilder.Marker parseName(PsiBuilder builder) {
    PsiBuilder.Marker nameElement = builder.mark();
    boolean empty = true;

    boolean idExpected = true;
    while (true) {
      IElementType t = builder.getTokenType();
      if (t == JavaTokenType.IDENTIFIER) {
        if (!idExpected) error(builder, JavaErrorMessages.message("expected.dot"));
        idExpected = false;
      }
      else if (t == JavaTokenType.DOT) {
        if (idExpected) error(builder, JavaErrorMessages.message("expected.identifier"));
        idExpected = true;
      }
      else break;
      builder.advanceLexer();
      empty = false;
    }

    if (!empty) {
      if (idExpected) error(builder, JavaErrorMessages.message("expected.identifier"));
      nameElement.done(JavaElementType.MODULE_REFERENCE);
      return nameElement;
    }
    else {
      nameElement.drop();
      return null;
    }
  }

  private void parseModuleContent(PsiBuilder builder) {
    IElementType token;
    PsiBuilder.Marker invalid = null;

    while ((token = builder.getTokenType()) != null) {
      if (token == JavaTokenType.RBRACE) {
        break;
      }

      if (token == JavaTokenType.SEMICOLON) {
        if (invalid != null) {
          invalid.error(JavaErrorMessages.message("expected.module.statement"));
          invalid = null;
        }
        builder.advanceLexer();
        continue;
      }

      PsiBuilder.Marker statement = parseStatement(builder);
      if (statement == null) {
        if (invalid == null) invalid = builder.mark();
        builder.advanceLexer();
      }
      else if (invalid != null) {
        invalid.errorBefore(JavaErrorMessages.message("expected.module.statement"), statement);
        invalid = null;
      }
    }

    if (invalid != null) {
      invalid.error(JavaErrorMessages.message("expected.module.statement"));
    }

    if (!expect(builder, JavaTokenType.RBRACE) && invalid == null) {
      error(builder, JavaErrorMessages.message("expected.rbrace"));
    }
  }

  private PsiBuilder.Marker parseStatement(PsiBuilder builder) {
    String kw = builder.getTokenText();
    if (PsiKeyword.REQUIRES.equals(kw)) return parseRequiresStatement(builder);
    if (PsiKeyword.EXPORTS.equals(kw)) return parseExportsStatement(builder);
    if (PsiKeyword.OPENS.equals(kw)) return parseOpensStatement(builder);
    if (PsiKeyword.USES.equals(kw)) return parseUsesStatement(builder);
    if (PsiKeyword.PROVIDES.equals(kw)) return parseProvidesStatement(builder);
    return null;
  }

  private static PsiBuilder.Marker parseRequiresStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    mapAndAdvance(builder, JavaTokenType.REQUIRES_KEYWORD);

    PsiBuilder.Marker modifierList = builder.mark();
    while (true) {
      if (expect(builder, ElementType.MODIFIER_BIT_SET)) continue;
      if (builder.getTokenType() == JavaTokenType.IDENTIFIER && PsiKeyword.TRANSITIVE.equals(builder.getTokenText())) {
        mapAndAdvance(builder, JavaTokenType.TRANSITIVE_KEYWORD);
        continue;
      }
      break;
    }
    JavaParserUtil.done(modifierList, JavaElementType.MODIFIER_LIST);

    if (parseNameRef(builder) != null) {
      semicolon(builder);
    }
    else {
      expect(builder, JavaTokenType.SEMICOLON);
    }

    statement.done(JavaElementType.REQUIRES_STATEMENT);
    return statement;
  }

  private PsiBuilder.Marker parseExportsStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    mapAndAdvance(builder, JavaTokenType.EXPORTS_KEYWORD);
    return parsePackageStatement(builder, statement, JavaElementType.EXPORTS_STATEMENT);
  }

  private PsiBuilder.Marker parseOpensStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    mapAndAdvance(builder, JavaTokenType.OPENS_KEYWORD);
    return parsePackageStatement(builder, statement, JavaElementType.OPENS_STATEMENT);
  }

  @NotNull
  private PsiBuilder.Marker parsePackageStatement(PsiBuilder builder, PsiBuilder.Marker statement, IElementType type) {
    boolean hasError = false;

    if (parseClassOrPackageRef(builder) != null) {
      if (PsiKeyword.TO.equals(builder.getTokenText())) {
        mapAndAdvance(builder, JavaTokenType.TO_KEYWORD);

        while (true) {
          PsiBuilder.Marker ref = parseNameRef(builder);
          if (!expect(builder, JavaTokenType.COMMA)) {
            if (ref == null) hasError = true;
            break;
          }
        }
      }
    }
    else {
      error(builder, JavaErrorMessages.message("expected.package.reference"));
      hasError = true;
    }

    if (!hasError) {
      semicolon(builder);
    }
    else {
      expect(builder, JavaTokenType.SEMICOLON);
    }

    statement.done(type);
    return statement;
  }

  private PsiBuilder.Marker parseUsesStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    mapAndAdvance(builder, JavaTokenType.USES_KEYWORD);

    if (parseClassOrPackageRef(builder) != null) {
      semicolon(builder);
    }
    else {
      error(builder, JavaErrorMessages.message("expected.class.reference"));
      expect(builder, JavaTokenType.SEMICOLON);
    }

    statement.done(JavaElementType.USES_STATEMENT);
    return statement;
  }

  private PsiBuilder.Marker parseProvidesStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    boolean hasError = false;
    mapAndAdvance(builder, JavaTokenType.PROVIDES_KEYWORD);

    if (parseClassOrPackageRef(builder) == null) {
      error(builder, JavaErrorMessages.message("expected.class.reference"));
      hasError = true;
    }

    if (PsiKeyword.WITH.equals(builder.getTokenText())) {
      builder.remapCurrentToken(JavaTokenType.WITH_KEYWORD);
      hasError = myParser.getReferenceParser().parseReferenceList(builder, JavaTokenType.WITH_KEYWORD, JavaElementType.PROVIDES_WITH_LIST, JavaTokenType.COMMA);
    }
    else if (!hasError) {
      IElementType next = builder.getTokenType();
      if (next == JavaTokenType.IDENTIFIER && !STATEMENT_KEYWORDS.contains(builder.getTokenText())) {
        PsiBuilder.Marker marker = builder.mark();
        builder.advanceLexer();
        marker.error(JavaErrorMessages.message("expected.with"));
      }
      else {
        error(builder, JavaErrorMessages.message("expected.with"));
      }
      hasError = true;
    }

    if (!hasError) {
      semicolon(builder);
    }
    else {
      expect(builder, JavaTokenType.SEMICOLON);
    }

    statement.done(JavaElementType.PROVIDES_STATEMENT);
    return statement;
  }

  private static PsiBuilder.Marker parseNameRef(PsiBuilder builder) {
    PsiBuilder.Marker name = parseName(builder);
    if (name == null) {
      error(builder, JavaErrorMessages.message("expected.identifier"));
    }
    return name;
  }

  private static void mapAndAdvance(PsiBuilder builder, IElementType keyword) {
    builder.remapCurrentToken(keyword);
    builder.advanceLexer();
  }

  private static void parseExtras(PsiBuilder builder, String message) {
    PsiBuilder.Marker extras = builder.mark();
    while (builder.getTokenType() != null) builder.advanceLexer();
    extras.error(message);
  }

  private PsiBuilder.Marker parseClassOrPackageRef(PsiBuilder builder) {
    return myParser.getReferenceParser().parseJavaCodeReference(builder, true, false, false, false);
  }
}
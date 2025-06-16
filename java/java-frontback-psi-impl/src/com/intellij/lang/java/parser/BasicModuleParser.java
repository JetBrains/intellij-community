// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.AbstractBasicJavaElementTypeFactory;
import com.intellij.psi.impl.source.BasicElementTypes;
import com.intellij.psi.impl.source.WhiteSpaceAndCommentSetHolder;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.BasicJavaParserUtil.error;
import static com.intellij.lang.java.parser.BasicJavaParserUtil.semicolon;

/**
 * @deprecated Use the new Java syntax library instead.
 *             See {@link com.intellij.java.syntax.parser.JavaParser}
 */
@Deprecated
public class BasicModuleParser {
  private static final Set<String> STATEMENT_KEYWORDS =
    ContainerUtil.newHashSet(JavaKeywords.REQUIRES, JavaKeywords.EXPORTS, JavaKeywords.USES, JavaKeywords.PROVIDES);

  private final BasicJavaParser myParser;
  private final AbstractBasicJavaElementTypeFactory.JavaElementTypeContainer myJavaElementTypeContainer;
  private final WhiteSpaceAndCommentSetHolder myWhiteSpaceAndCommentSetHolder = WhiteSpaceAndCommentSetHolder.INSTANCE;

  public BasicModuleParser(@NotNull BasicJavaParser parser) {
    myParser = parser;
    myJavaElementTypeContainer = parser.getJavaElementTypeFactory().getContainer();
  }

  public @Nullable PsiBuilder.Marker parse(@NotNull PsiBuilder builder) {
    PsiBuilder.Marker module = builder.mark();

    PsiBuilder.Marker firstAnnotation = myParser.getDeclarationParser().parseAnnotations(builder);

    IElementType type = builder.getTokenType();
    String text = type == JavaTokenType.IDENTIFIER ? builder.getTokenText() : null;
    if (!(JavaKeywords.OPEN.equals(text) || JavaKeywords.MODULE.equals(text))) {
      module.rollbackTo();
      return null;
    }

    PsiBuilder.Marker modifierList = firstAnnotation != null ? firstAnnotation.precede() : builder.mark();
    if (JavaKeywords.OPEN.equals(text)) {
      mapAndAdvance(builder, JavaTokenType.OPEN_KEYWORD);
      text = builder.getTokenText();
    }
    BasicJavaParserUtil.done(modifierList, myJavaElementTypeContainer.MODIFIER_LIST, builder, myWhiteSpaceAndCommentSetHolder);

    if (JavaKeywords.MODULE.equals(text)) {
      mapAndAdvance(builder, JavaTokenType.MODULE_KEYWORD);
    }
    else {
      module.drop();
      parseExtras(builder, JavaPsiBundle.message("expected.module.declaration"));
      return module;
    }

    if (parseName(builder) == null) {
      module.drop();
      if (builder.getTokenType() != null) {
        parseExtras(builder, JavaPsiBundle.message("expected.module.declaration"));
      }
      else {
        error(builder, JavaPsiBundle.message("expected.identifier"));
      }
      return module;
    }

    if (!expect(builder, JavaTokenType.LBRACE)) {
      if (builder.getTokenType() != null) {
        parseExtras(builder, JavaPsiBundle.message("expected.module.declaration"));
      }
      else {
        error(builder, JavaPsiBundle.message("expected.lbrace"));
      }
    }
    else {
      parseModuleContent(builder);
    }

    BasicJavaParserUtil.done(module, myJavaElementTypeContainer.MODULE, builder, myWhiteSpaceAndCommentSetHolder);

    if (builder.getTokenType() != null) {
      parseExtras(builder, JavaPsiBundle.message("unexpected.tokens"));
    }

    return module;
  }

  public PsiBuilder.Marker parseName(PsiBuilder builder) {
    PsiBuilder.Marker nameElement = builder.mark();
    boolean empty = true;

    boolean idExpected = true;
    while (true) {
      IElementType t = builder.getTokenType();
      if (t == JavaTokenType.IDENTIFIER) {
        if (!idExpected) error(builder, JavaPsiBundle.message("expected.dot"));
        idExpected = false;
      }
      else if (t == JavaTokenType.DOT) {
        if (idExpected) error(builder, JavaPsiBundle.message("expected.identifier"));
        idExpected = true;
      }
      else break;
      builder.advanceLexer();
      empty = false;
    }

    if (!empty) {
      if (idExpected) error(builder, JavaPsiBundle.message("expected.identifier"));
      nameElement.done(myJavaElementTypeContainer.MODULE_REFERENCE);
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
          invalid.error(JavaPsiBundle.message("expected.module.statement"));
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
        invalid.errorBefore(JavaPsiBundle.message("expected.module.statement"), statement);
        invalid = null;
      }
    }

    if (invalid != null) {
      invalid.error(JavaPsiBundle.message("expected.module.statement"));
    }

    if (!expect(builder, JavaTokenType.RBRACE) && invalid == null) {
      error(builder, JavaPsiBundle.message("expected.rbrace"));
    }
  }

  private PsiBuilder.Marker parseStatement(PsiBuilder builder) {
    String kw = builder.getTokenText();
    if (JavaKeywords.REQUIRES.equals(kw)) return parseRequiresStatement(builder);
    if (JavaKeywords.EXPORTS.equals(kw)) return parseExportsStatement(builder);
    if (JavaKeywords.OPENS.equals(kw)) return parseOpensStatement(builder);
    if (JavaKeywords.USES.equals(kw)) return parseUsesStatement(builder);
    if (JavaKeywords.PROVIDES.equals(kw)) return parseProvidesStatement(builder);
    return null;
  }

  private PsiBuilder.Marker parseRequiresStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    mapAndAdvance(builder, JavaTokenType.REQUIRES_KEYWORD);

    PsiBuilder.Marker modifierList = builder.mark();
    while (true) {
      if (expect(builder, BasicElementTypes.BASIC_MODIFIER_BIT_SET)) continue;
      if (builder.getTokenType() == JavaTokenType.IDENTIFIER && JavaKeywords.TRANSITIVE.equals(builder.getTokenText())) {
        mapAndAdvance(builder, JavaTokenType.TRANSITIVE_KEYWORD);
        continue;
      }
      break;
    }
    BasicJavaParserUtil.done(modifierList, myJavaElementTypeContainer.MODIFIER_LIST, builder, myWhiteSpaceAndCommentSetHolder);

    if (parseNameRef(builder) != null) {
      semicolon(builder);
    }
    else {
      expect(builder, JavaTokenType.SEMICOLON);
    }

    statement.done(myJavaElementTypeContainer.REQUIRES_STATEMENT);
    return statement;
  }

  private PsiBuilder.Marker parseExportsStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    mapAndAdvance(builder, JavaTokenType.EXPORTS_KEYWORD);
    return parsePackageStatement(builder, statement, myJavaElementTypeContainer.EXPORTS_STATEMENT);
  }

  private PsiBuilder.Marker parseOpensStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    mapAndAdvance(builder, JavaTokenType.OPENS_KEYWORD);
    return parsePackageStatement(builder, statement, myJavaElementTypeContainer.OPENS_STATEMENT);
  }

  private @NotNull PsiBuilder.Marker parsePackageStatement(PsiBuilder builder, PsiBuilder.Marker statement, IElementType type) {
    boolean hasError = false;

    if (parseClassOrPackageRef(builder) != null) {
      if (JavaKeywords.TO.equals(builder.getTokenText())) {
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
      error(builder, JavaPsiBundle.message("expected.package.reference"));
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
      error(builder, JavaPsiBundle.message("expected.class.reference"));
      expect(builder, JavaTokenType.SEMICOLON);
    }

    statement.done(myJavaElementTypeContainer.USES_STATEMENT);
    return statement;
  }

  private PsiBuilder.Marker parseProvidesStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    boolean hasError = false;
    mapAndAdvance(builder, JavaTokenType.PROVIDES_KEYWORD);

    if (parseClassOrPackageRef(builder) == null) {
      error(builder, JavaPsiBundle.message("expected.class.reference"));
      hasError = true;
    }

    if (JavaKeywords.WITH.equals(builder.getTokenText())) {
      builder.remapCurrentToken(JavaTokenType.WITH_KEYWORD);
      hasError = myParser.getReferenceParser().parseReferenceList(builder, JavaTokenType.WITH_KEYWORD, myJavaElementTypeContainer.PROVIDES_WITH_LIST, JavaTokenType.COMMA);
    }
    else if (!hasError) {
      IElementType next = builder.getTokenType();
      if (next == JavaTokenType.IDENTIFIER && !STATEMENT_KEYWORDS.contains(builder.getTokenText())) {
        PsiBuilder.Marker marker = builder.mark();
        builder.advanceLexer();
        marker.error(JavaPsiBundle.message("expected.with"));
      }
      else {
        error(builder, JavaPsiBundle.message("expected.with"));
      }
      hasError = true;
    }

    if (!hasError) {
      semicolon(builder);
    }
    else {
      expect(builder, JavaTokenType.SEMICOLON);
    }

    statement.done(myJavaElementTypeContainer.PROVIDES_STATEMENT);
    return statement;
  }

  private PsiBuilder.Marker parseNameRef(PsiBuilder builder) {
    PsiBuilder.Marker name = parseName(builder);
    if (name == null) {
      error(builder, JavaPsiBundle.message("expected.identifier"));
    }
    return name;
  }

  private static void mapAndAdvance(PsiBuilder builder, IElementType keyword) {
    builder.remapCurrentToken(keyword);
    builder.advanceLexer();
  }

  private static void parseExtras(PsiBuilder builder, @NotNull @NlsContexts.ParsingError String message) {
    PsiBuilder.Marker extras = builder.mark();
    while (builder.getTokenType() != null) builder.advanceLexer();
    extras.error(message);
  }

  private PsiBuilder.Marker parseClassOrPackageRef(PsiBuilder builder) {
    return myParser.getReferenceParser().parseJavaCodeReference(builder, true, false, false, false);
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.AbstractBundle;
import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.impl.source.AbstractBasicJavaElementTypeFactory;
import com.intellij.psi.impl.source.BasicElementTypes;
import com.intellij.psi.impl.source.WhiteSpaceAndCommentSetHolder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.BasicJavaParserUtil.*;

public class BasicFileParser {
  protected final TokenSet IMPORT_LIST_STOPPER_SET;
  private final BasicJavaParser myParser;
  private final AbstractBasicJavaElementTypeFactory.JavaElementTypeContainer myJavaElementTypeContainer;

  private final TokenSet IMPLICIT_CLASS_INDICATORS;

  private final WhiteSpaceAndCommentSetHolder myWhiteSpaceAndCommentSetHolder = WhiteSpaceAndCommentSetHolder.INSTANCE;

  public BasicFileParser(@NotNull BasicJavaParser javaParser) {
    myParser = javaParser;
    myJavaElementTypeContainer = javaParser.getJavaElementTypeFactory().getContainer();
    IMPORT_LIST_STOPPER_SET = TokenSet.orSet(
      BasicElementTypes.BASIC_MODIFIER_BIT_SET,
      TokenSet.create(JavaTokenType.CLASS_KEYWORD, JavaTokenType.INTERFACE_KEYWORD, JavaTokenType.ENUM_KEYWORD, JavaTokenType.AT));
    IMPLICIT_CLASS_INDICATORS =
    TokenSet.create(myJavaElementTypeContainer.METHOD, myJavaElementTypeContainer.FIELD, myJavaElementTypeContainer.CLASS_INITIALIZER);
  }

  public void parse(@NotNull PsiBuilder builder) {
    parseFile(builder, this::stopImportListParsing, JavaPsiBundle.INSTANCE, "expected.class.or.interface");
  }

  public void parseFile(@NotNull PsiBuilder builder,
                        @NotNull Predicate<? super PsiBuilder> importListStopper,
                        @NotNull AbstractBundle bundle,
                        @NotNull String errorMessageKey) {
    parsePackageStatement(builder);

    Pair<PsiBuilder.Marker, Boolean> impListInfo = parseImportList(builder, importListStopper);  // (importList, isEmpty)
    Boolean firstDeclarationOk = null;
    PsiBuilder.Marker firstDeclaration = null;

    PsiBuilder.Marker invalidElements = null;
    boolean isImplicitClass = false;
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
          firstDeclarationOk = exprType(declaration) != myJavaElementTypeContainer.MODIFIER_LIST;
        }
        if (firstDeclaration == null) {
          firstDeclaration = declaration;
        }
        if (!isImplicitClass && IMPLICIT_CLASS_INDICATORS.contains(exprType(declaration))) {
          isImplicitClass = true;
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
      impListInfo.first.setCustomEdgeTokenBinders(myWhiteSpaceAndCommentSetHolder.getPrecedingCommentBinder(), null);  // pass comments behind fake import list
      firstDeclaration.setCustomEdgeTokenBinders(myWhiteSpaceAndCommentSetHolder.getSpecialPrecedingCommentBinder(), null);
    }
    if (isImplicitClass) {
      PsiBuilder.Marker beforeFirst = firstDeclaration.precede();
      done(beforeFirst, myJavaElementTypeContainer.IMPLICIT_CLASS, myWhiteSpaceAndCommentSetHolder);
    }
  }

  private boolean stopImportListParsing(PsiBuilder b) {
    IElementType type = b.getTokenType();
    if (IMPORT_LIST_STOPPER_SET.contains(type) || myParser.getDeclarationParser().isRecordToken(b, type)) return true;
    if (type == JavaTokenType.IDENTIFIER) {
      String text = b.getTokenText();
      if (PsiKeyword.OPEN.equals(text) || PsiKeyword.MODULE.equals(text)) return true;
    }
    return false;
  }

  @Nullable
  protected PsiBuilder.Marker parseInitial(PsiBuilder builder) {
    return myParser.getDeclarationParser().parse(builder, BasicDeclarationParser.BaseContext.FILE);
  }

  private void parsePackageStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();

    if (!expect(builder, JavaTokenType.PACKAGE_KEYWORD)) {
      PsiBuilder.Marker modList = builder.mark();
      myParser.getDeclarationParser().parseAnnotations(builder);
      done(modList, myJavaElementTypeContainer.MODIFIER_LIST, myWhiteSpaceAndCommentSetHolder);
      if (!expect(builder, JavaTokenType.PACKAGE_KEYWORD)) {
        statement.rollbackTo();
        return;
      }
    }

    PsiBuilder.Marker ref = myParser.getReferenceParser().parseJavaCodeReference(builder, true, false, false, false);
    if (ref == null) {
      statement.error(JavaPsiBundle.message("expected.class.or.interface"));
      return;
    }

    semicolon(builder);

    done(statement, myJavaElementTypeContainer.PACKAGE_STATEMENT, myWhiteSpaceAndCommentSetHolder);
  }

  @NotNull
  protected Pair<PsiBuilder.Marker, Boolean> parseImportList(PsiBuilder builder, Predicate<? super PsiBuilder> stopper) {
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

      PsiBuilder.Marker statement = parseImportStatement(builder);
      if (statement != null) {
        isEmpty = false;
        if (invalidElements != null) {
          invalidElements.errorBefore(JavaPsiBundle.message("unexpected.token"), statement);
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
      invalidElements.rollbackTo();
    }

    if (isEmpty) {
      PsiBuilder.Marker precede = list.precede();
      list.rollbackTo();
      list = precede;
    }

    done(list, myJavaElementTypeContainer.IMPORT_LIST, myWhiteSpaceAndCommentSetHolder);
    return Pair.create(list, isEmpty);
  }

  @Nullable
  protected PsiBuilder.Marker parseImportStatement(PsiBuilder builder) {
    if (builder.getTokenType() != JavaTokenType.IMPORT_KEYWORD) return null;

    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    String identifierText = builder.getTokenText();
    IElementType type = getImportType(builder);
    boolean isStatic = type == myJavaElementTypeContainer.IMPORT_STATIC_STATEMENT;
    boolean isModule = type == myJavaElementTypeContainer.IMPORT_MODULE_STATEMENT;
    final boolean isOk;
    if (isModule) {
      isOk = myParser.getModuleParser().parseName(builder) != null;
    } else {
      isOk = myParser.getReferenceParser().parseImportCodeReference(builder, isStatic);
    }

    //if it is `module` we should expect either `;` or `identifier`
    if (isOk && !isModule && !isStatic && builder.getTokenType() != JavaTokenType.SEMICOLON &&
        PsiKeyword.MODULE.equals(identifierText)) {
      BasicJavaParserUtil.error(builder, JavaPsiBundle.message("expected.identifier.or.semicolon"));
    }
    else if (isOk) {
      semicolon(builder);
    }

    done(statement, type, myWhiteSpaceAndCommentSetHolder);
    return statement;
  }

  private @NotNull IElementType getImportType(@NotNull PsiBuilder builder) {
    IElementType type = builder.getTokenType();
    if (type == JavaTokenType.STATIC_KEYWORD) {
      builder.advanceLexer();
      return myJavaElementTypeContainer.IMPORT_STATIC_STATEMENT;
    }
    if (type == JavaTokenType.IDENTIFIER &&
        PsiKeyword.MODULE.equals(builder.getTokenText()) &&
        builder.lookAhead(1) == JavaTokenType.IDENTIFIER) {
      builder.remapCurrentToken(JavaTokenType.MODULE_KEYWORD);
      builder.advanceLexer();
      return myJavaElementTypeContainer.IMPORT_MODULE_STATEMENT;
    }
    return myJavaElementTypeContainer.IMPORT_STATEMENT;
  }

  @NotNull
  private static @NlsContexts.ParsingError String error(@NotNull AbstractBundle bundle, @NotNull String errorMessageKey) {
    return bundle.getMessage(errorMessageKey);
  }
}
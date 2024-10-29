// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderAdapter;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.lexer.BasicJavaLexer;
import com.intellij.lang.java.lexer.JavaDocLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.TokenList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.ParsingDiagnostics;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.BasicJavaAstTreeUtil;
import com.intellij.psi.impl.source.WhiteSpaceAndCommentSetHolder;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.indexing.IndexingDataKeys;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.intellij.psi.impl.source.BasicJavaDocElementType.BASIC_DOC_COMMENT;

@ApiStatus.Experimental
public final class BasicJavaParserUtil {
  private static final Key<LanguageLevel> LANG_LEVEL_KEY = Key.create("JavaParserUtil.LanguageLevel");
  private static final Key<Boolean> DEEP_PARSE_BLOCKS_IN_STATEMENTS = Key.create("JavaParserUtil.ParserExtender");


  private BasicJavaParserUtil() { }

  public static void setLanguageLevel(final PsiBuilder builder, final LanguageLevel level) {
    builder.putUserData(LANG_LEVEL_KEY, level);
  }

  @NotNull
  public static LanguageLevel getLanguageLevel(final PsiBuilder builder) {
    final LanguageLevel level = builder.getUserData(LANG_LEVEL_KEY);
    assert level != null : builder;
    return level;
  }

  public static void setParseStatementCodeBlocksDeep(final PsiBuilder builder, final boolean deep) {
    builder.putUserData(DEEP_PARSE_BLOCKS_IN_STATEMENTS, deep);
  }

  public static boolean isParseStatementCodeBlocksDeep(final PsiBuilder builder) {
    return Boolean.TRUE.equals(builder.getUserData(DEEP_PARSE_BLOCKS_IN_STATEMENTS));
  }

  public static void done(final PsiBuilder.Marker marker, final IElementType type, final WhiteSpaceAndCommentSetHolder commentSetHolder) {
    marker.done(type);
    final WhitespacesAndCommentsBinder left =
      commentSetHolder.getPrecedingCommentSet().contains(type) ? commentSetHolder.getPrecedingCommentBinder()
                                                               : null;
    final WhitespacesAndCommentsBinder right =
      commentSetHolder.getTrailingCommentSet().contains(type) ? commentSetHolder.getTrailingCommentBinder()
                                                              : null;
    marker.setCustomEdgeTokenBinders(left, right);
  }

  @Nullable
  public static IElementType exprType(@Nullable final PsiBuilder.Marker marker) {
    return marker != null ? marker.getTokenType() : null;
  }

  // used instead of PsiBuilder.error() as it keeps all subsequent error messages
  public static void error(final PsiBuilder builder, @NotNull @NlsContexts.ParsingError String message) {
    builder.mark().error(message);
  }

  public static void error(final PsiBuilder builder,
                           @NotNull @NlsContexts.ParsingError String message,
                           @Nullable final PsiBuilder.Marker before) {
    if (before == null) {
      error(builder, message);
    }
    else {
      before.precede().errorBefore(message, before);
    }
  }

  public static boolean expectOrError(PsiBuilder builder,
                                      TokenSet expected,
                                      @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String key) {
    if (!PsiBuilderUtil.expect(builder, expected)) {
      error(builder, JavaPsiBundle.message(key));
      return false;
    }
    return true;
  }

  public static boolean expectOrError(PsiBuilder builder,
                                      IElementType expected,
                                      @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String key) {
    if (!PsiBuilderUtil.expect(builder, expected)) {
      error(builder, JavaPsiBundle.message(key));
      return false;
    }
    return true;
  }

  public static void emptyElement(final PsiBuilder builder, final IElementType type) {
    builder.mark().done(type);
  }

  public static void emptyElement(final PsiBuilder.Marker before, final IElementType type) {
    before.precede().doneBefore(type, before);
  }

  public static void semicolon(final PsiBuilder builder) {
    expectOrError(builder, JavaTokenType.SEMICOLON, "expected.semicolon");
  }

  public static PsiBuilder braceMatchingBuilder(final PsiBuilder builder) {
    final PsiBuilder.Marker pos = builder.mark();

    int braceCount = 1;
    while (!builder.eof()) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == JavaTokenType.LBRACE) {
        braceCount++;
      }
      else if (tokenType == JavaTokenType.RBRACE) braceCount--;
      if (braceCount == 0) break;
      builder.advanceLexer();
    }
    final int stopAt = builder.getCurrentOffset();

    pos.rollbackTo();

    return stoppingBuilder(builder, stopAt);
  }

  public static PsiBuilder stoppingBuilder(final PsiBuilder builder, final int stopAt) {
    return new PsiBuilderAdapter(builder) {
      @Override
      public IElementType getTokenType() {
        return getCurrentOffset() < stopAt ? super.getTokenType() : null;
      }

      @Override
      public boolean eof() {
        return getCurrentOffset() >= stopAt || super.eof();
      }
    };
  }

  @FunctionalInterface
  public interface ParserWrapper {
    void parse(PsiBuilder builder);
  }

  public static PsiBuilder stoppingBuilder(final PsiBuilder builder, final Predicate<? super Pair<IElementType, String>> condition) {
    return new PsiBuilderAdapter(builder) {
      @Override
      public IElementType getTokenType() {
        final Pair<IElementType, String> input = Pair.create(builder.getTokenType(), builder.getTokenText());
        return condition.test(input) ? null : super.getTokenType();
      }

      @Override
      public boolean eof() {
        final Pair<IElementType, String> input = Pair.create(builder.getTokenType(), builder.getTokenText());
        return condition.test(input) || super.eof();
      }
    };
  }

  @Nullable
  public static ASTNode parseFragment(final ASTNode chameleon, final BasicJavaParserUtil.ParserWrapper wrapper,
                                      Function<LanguageLevel, JavaDocLexer> javaDocLexer,
                                      Function<LanguageLevel, BasicJavaLexer> javaLexer) {
    return parseFragment(chameleon, wrapper, true, LanguageLevel.HIGHEST, javaDocLexer, javaLexer);
  }

  @Nullable
  public static ASTNode parseFragment(final ASTNode chameleon,
                                      final BasicJavaParserUtil.ParserWrapper wrapper,
                                      final boolean eatAll,
                                      final LanguageLevel level,
                                      Function<LanguageLevel, JavaDocLexer> javaDocLexer,
                                      Function<LanguageLevel, ? extends Lexer> javaLexer) {
    final PsiElement psi = chameleon.getTreeParent() != null ? chameleon.getTreeParent().getPsi() : chameleon.getPsi();
    assert psi != null : chameleon;
    final Project project = psi.getProject();

    final PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
    final Lexer lexer = BasicJavaAstTreeUtil.is(chameleon, BASIC_DOC_COMMENT) ? javaDocLexer.apply(level) : javaLexer.apply(level);
    final PsiBuilder builder =
      factory.createBuilder(project, chameleon, lexer, chameleon.getElementType().getLanguage(), chameleon.getChars());
    setLanguageLevel(builder, level);

    long startTime = System.nanoTime();
    final PsiBuilder.Marker root = builder.mark();
    wrapper.parse(builder);
    if (!builder.eof()) {
      if (!eatAll) throw new AssertionError("Unexpected token: '" + builder.getTokenText() + "'");
      final PsiBuilder.Marker extras = builder.mark();
      while (!builder.eof()) builder.advanceLexer();
      extras.error(JavaPsiBundle.message("unexpected.tokens"));
    }
    root.done(chameleon.getElementType());
    ASTNode result = builder.getTreeBuilt().getFirstChildNode();
    ParsingDiagnostics.registerParse(builder, chameleon.getElementType().getLanguage(), System.nanoTime() - startTime);
    return result;
  }


  @NotNull
  public static PsiBuilder createBuilder(@NotNull final ASTNode chameleon,
                                         @NotNull Function<PsiElement, LanguageLevel> languageLevelFunction,
                                         @NotNull Function<LanguageLevel, BasicJavaLexer> lexerFunction,
                                         @NotNull Function<PsiFile, TokenList> psiAsLexer) {
    final PsiElement psi = chameleon.getPsi();
    assert psi != null : chameleon;
    final Project project = psi.getProject();

    CharSequence indexedText = psi.getUserData(IndexingDataKeys.FILE_TEXT_CONTENT_KEY);

    CharSequence text;
    if (TreeUtil.isCollapsedChameleon(chameleon)) {
      text = chameleon.getChars();
    }
    else {
      text = indexedText;
      if (text == null) text = chameleon.getChars();
    }

    LanguageLevel level = languageLevelFunction.apply(psi);
    Lexer lexer = psi instanceof PsiFile && indexedText != null ? psiAsLexer.apply((PsiFile)psi).asLexer()
                                                                : lexerFunction.apply(level);
    Language language = psi.getLanguage();
    if (!language.isKindOf(JavaLanguage.INSTANCE)) language = JavaLanguage.INSTANCE;
    PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, lexer, language, text);
    setLanguageLevel(builder, level);

    return builder;
  }

  @NotNull
  public static PsiBuilder createBuilder(@NotNull LighterLazyParseableNode chameleon,
                                         @NotNull Function<PsiElement, LanguageLevel> languageLevelFunction,
                                         @NotNull Function<LanguageLevel, BasicJavaLexer> lexerFunction) {
    final PsiElement psi = chameleon.getContainingFile();
    assert psi != null : chameleon;
    final Project project = psi.getProject();

    final PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
    final LanguageLevel level = languageLevelFunction.apply(psi);
    final Lexer lexer = lexerFunction.apply(level);
    final PsiBuilder builder =
      factory.createBuilder(project, chameleon, lexer, chameleon.getTokenType().getLanguage(), chameleon.getText());
    setLanguageLevel(builder, level);

    return builder;
  }
}
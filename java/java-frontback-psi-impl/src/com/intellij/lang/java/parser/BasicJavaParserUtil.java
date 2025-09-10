// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.java.syntax.element.lazyParser.ParsingUtil;
import com.intellij.java.syntax.lexer.JavaLexer;
import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderAdapter;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.platform.syntax.lexer.TokenList;
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder;
import com.intellij.platform.syntax.psi.*;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.WhiteSpaceAndCommentSetHolder;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.indexing.IndexingDataKeys;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.intellij.platform.syntax.lexer.TokenListUtil.tokenListLexer;

@ApiStatus.Experimental
public final class BasicJavaParserUtil {
  private static final Key<LanguageLevel> LANG_LEVEL_KEY = Key.create("JavaParserUtil.LanguageLevel");
  private static final Key<Boolean> DEEP_PARSE_BLOCKS_IN_STATEMENTS = Key.create("JavaParserUtil.ParserExtender");

  private static final com.intellij.platform.syntax.Logger SYNTAX_LOGGER = IntelliJLogger.asSyntaxLogger(Logger.getInstance(BasicJavaParserUtil.class));

  private BasicJavaParserUtil() { }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static void setLanguageLevel(final PsiBuilder builder, final LanguageLevel level) {
    builder.putUserData(LANG_LEVEL_KEY, level);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static @NotNull LanguageLevel getLanguageLevel(final PsiBuilder builder) {
    final LanguageLevel level = builder.getUserData(LANG_LEVEL_KEY);
    assert level != null : builder;
    return level;
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static void setParseStatementCodeBlocksDeep(final PsiBuilder builder, final boolean deep) {
    builder.putUserData(DEEP_PARSE_BLOCKS_IN_STATEMENTS, deep);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static boolean isParseStatementCodeBlocksDeep(final PsiBuilder builder) {
    return Boolean.TRUE.equals(builder.getUserData(DEEP_PARSE_BLOCKS_IN_STATEMENTS));
  }

  /**
   * @deprecated use {@link BasicJavaParserUtil#done(PsiBuilder.Marker, IElementType, PsiBuilder, WhiteSpaceAndCommentSetHolder)}
   */
  @Deprecated
  public static void done(final @NotNull PsiBuilder.Marker marker,
                          final @NotNull IElementType type,
                          final @NotNull WhiteSpaceAndCommentSetHolder commentSetHolder) {
    marker.done(type);
    final WhitespacesAndCommentsBinder left =
      commentSetHolder.getPrecedingCommentSet().contains(type) ? commentSetHolder.getPrecedingCommentBinder(LanguageLevel.HIGHEST)
                                                               : null;
    final WhitespacesAndCommentsBinder right =
      commentSetHolder.getTrailingCommentSet().contains(type) ? commentSetHolder.getTrailingCommentBinder()
                                                              : null;
    marker.setCustomEdgeTokenBinders(left, right);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static void done(final @NotNull PsiBuilder.Marker marker,
                          final @NotNull IElementType type,
                          final @NotNull PsiBuilder builder,
                          final @NotNull WhiteSpaceAndCommentSetHolder commentSetHolder) {
    marker.done(type);
    final WhitespacesAndCommentsBinder left =
      commentSetHolder.getPrecedingCommentSet().contains(type) ? commentSetHolder.getPrecedingCommentBinder(getLanguageLevel(builder))
                                                               : null;
    final WhitespacesAndCommentsBinder right =
      commentSetHolder.getTrailingCommentSet().contains(type) ? commentSetHolder.getTrailingCommentBinder()
                                                              : null;
    marker.setCustomEdgeTokenBinders(left, right);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static @Nullable IElementType exprType(final @Nullable PsiBuilder.Marker marker) {
    return marker != null ? marker.getTokenType() : null;
  }

  // used instead of PsiBuilder.error() as it keeps all subsequent error messages
  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static void error(final PsiBuilder builder, @NotNull @NlsContexts.ParsingError String message) {
    builder.mark().error(message);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static void error(final PsiBuilder builder,
                           @NotNull @NlsContexts.ParsingError String message,
                           final @Nullable PsiBuilder.Marker before) {
    if (before == null) {
      error(builder, message);
    }
    else {
      before.precede().errorBefore(message, before);
    }
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static boolean expectOrError(PsiBuilder builder,
                                      TokenSet expected,
                                      @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String key) {
    if (!PsiBuilderUtil.expect(builder, expected)) {
      error(builder, JavaPsiBundle.message(key));
      return false;
    }
    return true;
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static boolean expectOrError(PsiBuilder builder,
                                      IElementType expected,
                                      @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String key) {
    if (!PsiBuilderUtil.expect(builder, expected)) {
      error(builder, JavaPsiBundle.message(key));
      return false;
    }
    return true;
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static void emptyElement(final PsiBuilder builder, final IElementType type) {
    builder.mark().done(type);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static void emptyElement(final PsiBuilder.Marker before, final IElementType type) {
    before.precede().doneBefore(type, before);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static void semicolon(final PsiBuilder builder) {
    expectOrError(builder, JavaTokenType.SEMICOLON, "expected.semicolon");
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
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

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
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
    void parse(@NotNull SyntaxTreeBuilder builder, @NotNull LanguageLevel languageLevel);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
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

  public static @Nullable ASTNode parseFragmentWithHighestLanguageLevel(
    @NotNull ASTNode chameleon,
    @NotNull BasicJavaParserUtil.ParserWrapper wrapper,
    @NotNull Function<LanguageLevel, Lexer> lexer
  ) {
    return parseFragment(chameleon, wrapper, true, LanguageLevel.HIGHEST, lexer);
  }

  public static @Nullable ASTNode parseFragment(
    @NotNull ASTNode chameleon,
    @NotNull BasicJavaParserUtil.ParserWrapper wrapper,
    boolean eatAll,
    @NotNull LanguageLevel level) {
    return parseFragment(chameleon, wrapper, eatAll, level, JavaLexer::new);
  }

  public static @Nullable ASTNode parseFragment(
    @NotNull ASTNode chameleon,
    @NotNull BasicJavaParserUtil.ParserWrapper wrapper,
    boolean eatAll,
    @NotNull LanguageLevel level,
    @NotNull Function<LanguageLevel, ? extends Lexer> javaLexer
  ) {
    final PsiElement psi = chameleon.getTreeParent() != null ? chameleon.getTreeParent().getPsi() : chameleon.getPsi();
    assert psi != null : chameleon;

    PsiSyntaxBuilderFactory factory = PsiSyntaxBuilderFactory.getInstance();
    Lexer lexer = javaLexer.apply(level);
    PsiSyntaxBuilder psiBuilder = factory.createBuilder(chameleon, lexer, chameleon.getElementType().getLanguage(), chameleon.getChars());

    long startTime = System.nanoTime();
    SyntaxTreeBuilder builder = psiBuilder.getSyntaxTreeBuilder();
    ElementTypeConverter converter = ElementTypeConverters.getConverter(JavaLanguage.INSTANCE);
    SyntaxElementType type = ElementTypeConverterKt.convertNotNull(converter, chameleon.getElementType());
    ParsingUtil.parseFragment(builder, type, eatAll, () -> {
      wrapper.parse(builder, level);
      return Unit.INSTANCE;
    });

    ASTNode result = psiBuilder.getTreeBuilt().getFirstChildNode();
    ParsingDiagnostics.registerParse(builder, chameleon.getElementType().getLanguage(), System.nanoTime() - startTime);
    return result;
  }


  public static @NotNull PsiSyntaxBuilderWithLanguageLevel createSyntaxBuilder(@NotNull ASTNode chameleon,
                                                                               @NotNull Function<PsiElement, LanguageLevel> languageLevelFunction,
                                                                               @NotNull Function<PsiFile, TokenList> psiAsLexer) {
    PsiElement psi = chameleon.getPsi();
    assert psi != null : chameleon;

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
    Lexer lexer = psi instanceof PsiFile && indexedText != null ? tokenListLexer(psiAsLexer.apply((PsiFile)psi), SYNTAX_LOGGER)
                                                                : new JavaLexer(level);
    Language language = psi.getLanguage();
    if (!language.isKindOf(JavaLanguage.INSTANCE)) language = JavaLanguage.INSTANCE;
    PsiSyntaxBuilderFactory factory = PsiSyntaxBuilderFactory.getInstance();
    PsiSyntaxBuilder builder = factory.createBuilder(chameleon, lexer, language, text);

    return new PsiSyntaxBuilderWithLanguageLevel(builder, level);
  }

  public static @NotNull PsiSyntaxBuilderWithLanguageLevel createSyntaxBuilder(@NotNull LighterLazyParseableNode chameleon,
                                                                               @NotNull Function<PsiElement, LanguageLevel> languageLevelFunction) {
    PsiElement psi = chameleon.getContainingFile();
    assert psi != null : chameleon;

    PsiSyntaxBuilderFactory factory = PsiSyntaxBuilderFactory.getInstance();
    LanguageLevel level = languageLevelFunction.apply(psi);
    Lexer lexer = new JavaLexer(level);
    PsiSyntaxBuilder builder = factory.createBuilder(chameleon, lexer, chameleon.getTokenType().getLanguage(), chameleon.getText());

    return new PsiSyntaxBuilderWithLanguageLevel(builder, level);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static @NotNull PsiBuilder createBuilder(final @NotNull ASTNode chameleon,
                                                  @NotNull Function<PsiElement, LanguageLevel> languageLevelFunction,
                                                  @NotNull Function<LanguageLevel, com.intellij.lexer.Lexer> lexerFunction,
                                                  @NotNull Function<PsiFile, com.intellij.lexer.TokenList> psiAsLexer) {
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
    com.intellij.lexer.Lexer lexer = psi instanceof PsiFile && indexedText != null ? psiAsLexer.apply((PsiFile)psi).asLexer()
                                                                                   : lexerFunction.apply(level);
    Language language = psi.getLanguage();
    if (!language.isKindOf(JavaLanguage.INSTANCE)) language = JavaLanguage.INSTANCE;
    PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, lexer, language, text);
    setLanguageLevel(builder, level);

    return builder;
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static @NotNull PsiBuilder createBuilder(@NotNull LighterLazyParseableNode chameleon,
                                                  @NotNull Function<PsiElement, LanguageLevel> languageLevelFunction,
                                                  @NotNull Function<LanguageLevel, com.intellij.lexer.Lexer> lexerFunction) {
    final PsiElement psi = chameleon.getContainingFile();
    assert psi != null : chameleon;
    final Project project = psi.getProject();

    final PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
    final LanguageLevel level = languageLevelFunction.apply(psi);
    final com.intellij.lexer.Lexer lexer = lexerFunction.apply(level);
    final PsiBuilder builder =
      factory.createBuilder(project, chameleon, lexer, chameleon.getTokenType().getLanguage(), chameleon.getText());
    setLanguageLevel(builder, level);

    return builder;
  }
}
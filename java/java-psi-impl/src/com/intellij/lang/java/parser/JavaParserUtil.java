// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.java.syntax.element.SyntaxElementTypes;
import com.intellij.java.syntax.lexer.JavaLexer;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterLazyParseableNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.WhitespacesAndCommentsBinder;
import com.intellij.lang.impl.TokenSequence;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.platform.syntax.SyntaxElementTypeSet;
import com.intellij.platform.syntax.element.SyntaxTokenTypes;
import com.intellij.platform.syntax.lexer.TokenList;
import com.intellij.platform.syntax.lexer.TokenListUtil;
import com.intellij.platform.syntax.psi.IntelliJLogger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.WhiteSpaceAndCommentSetHolder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Predicate;

public final class JavaParserUtil {
  public static final SyntaxElementTypeSet WS_COMMENTS =
    SyntaxElementTypes.INSTANCE.getJAVA_COMMENT_BIT_SET().plus(SyntaxTokenTypes.getWHITE_SPACE());

  private static final com.intellij.platform.syntax.Logger SYNTAX_LOGGER =
    IntelliJLogger.asSyntaxLogger(Logger.getInstance(JavaParserUtil.class));

  public static @NotNull TokenList obtainTokens(@NotNull PsiFile file) {
    return CachedValuesManager.getCachedValue(file, () ->
      CachedValueProvider.Result.create(
        TokenListUtil.performLexing(
          file.getViewProvider().getContents(),
          new JavaLexer(PsiUtil.getLanguageLevel(file)),
          ProgressManager::checkCanceled,
          SYNTAX_LOGGER
        ), file));
  }

  /**
   * @deprecated Use {@link #obtainTokens(PsiFile)} instead
   */
  @Deprecated
  public static @NotNull com.intellij.lexer.TokenList obtainTokensOutdated(@NotNull PsiFile file) {
    return CachedValuesManager.getCachedValue(file, () ->
      CachedValueProvider.Result.create(
        TokenSequence.performLexing(file.getViewProvider().getContents(), JavaParserDefinition.createLexer(PsiUtil.getLanguageLevel(file))),
        file));
  }

  @FunctionalInterface
  public interface ParserWrapper extends BasicJavaParserUtil.ParserWrapper {
  }

  /**
   * @deprecated please, use {@link WhiteSpaceAndCommentSetHolder#INSTANCE} instead
   */
  @Deprecated
  public static final WhitespacesAndCommentsBinder PRECEDING_COMMENT_BINDER =
    WhiteSpaceAndCommentSetHolder.INSTANCE.getPrecedingCommentBinder(LanguageLevel.HIGHEST);

  /**
   * @deprecated please, use {@link WhiteSpaceAndCommentSetHolder#INSTANCE} instead
   */
  @Deprecated
  public static final WhitespacesAndCommentsBinder SPECIAL_PRECEDING_COMMENT_BINDER =
    WhiteSpaceAndCommentSetHolder.INSTANCE.getSpecialPrecedingCommentBinder(LanguageLevel.HIGHEST);

  /**
   * @deprecated please, use {@link WhiteSpaceAndCommentSetHolder#INSTANCE} instead
   */
  @Deprecated
  public static final WhitespacesAndCommentsBinder TRAILING_COMMENT_BINDER =
    WhiteSpaceAndCommentSetHolder.INSTANCE.getTrailingCommentBinder();


  private JavaParserUtil() { }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static void setLanguageLevel(final PsiBuilder builder, final LanguageLevel level) {
    BasicJavaParserUtil.setLanguageLevel(builder, level);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static @NotNull LanguageLevel getLanguageLevel(final PsiBuilder builder) {
    return BasicJavaParserUtil.getLanguageLevel(builder);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static void setParseStatementCodeBlocksDeep(final PsiBuilder builder, final boolean deep) {
    BasicJavaParserUtil.setParseStatementCodeBlocksDeep(builder, deep);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static boolean isParseStatementCodeBlocksDeep(final PsiBuilder builder) {
    return BasicJavaParserUtil.isParseStatementCodeBlocksDeep(builder);
  }

  public static @NotNull PsiSyntaxBuilderWithLanguageLevel createSyntaxBuilder(final ASTNode chameleon) {
    return BasicJavaParserUtil.createSyntaxBuilder(chameleon,
                                             (psi) -> PsiUtil.getLanguageLevel(psi),
                                             (psi) -> obtainTokens(psi));
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static @NotNull PsiBuilder createBuilder(final ASTNode chameleon) {
    return BasicJavaParserUtil.createBuilder(chameleon,
                                             (psi) -> PsiUtil.getLanguageLevel(psi),
                                             (level) -> JavaParserDefinition.createLexer(level),
                                             (psi) -> obtainTokensOutdated(psi));
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static @NotNull PsiBuilder createBuilder(final LighterLazyParseableNode chameleon) {
    return BasicJavaParserUtil.createBuilder(chameleon,
                                             (psi) -> PsiUtil.getLanguageLevel(psi),
                                             (level) -> JavaParserDefinition.createLexer(level));
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static void done(final PsiBuilder.Marker marker, final IElementType type) {
    BasicJavaParserUtil.done(marker, type, WhiteSpaceAndCommentSetHolder.INSTANCE);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static @Nullable IElementType exprType(final @Nullable PsiBuilder.Marker marker) {
    return BasicJavaParserUtil.exprType(marker);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static void error(final PsiBuilder builder, @NotNull @NlsContexts.ParsingError String message) {
    BasicJavaParserUtil.error(builder, message);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static void error(final PsiBuilder builder,
                           @NotNull @NlsContexts.ParsingError String message,
                           final @Nullable PsiBuilder.Marker before) {
    BasicJavaParserUtil.error(builder, message, before);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static boolean expectOrError(PsiBuilder builder,
                                      TokenSet expected,
                                      @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String key) {
    return BasicJavaParserUtil.expectOrError(builder, expected, key);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static boolean expectOrError(PsiBuilder builder,
                                      IElementType expected,
                                      @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String key) {
    return BasicJavaParserUtil.expectOrError(builder, expected, key);
  }


  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static void emptyElement(final PsiBuilder builder, final IElementType type) {
    BasicJavaParserUtil.emptyElement(builder, type);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static void emptyElement(final PsiBuilder.Marker before, final IElementType type) {
    BasicJavaParserUtil.emptyElement(before, type);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static void semicolon(final PsiBuilder builder) {
    BasicJavaParserUtil.semicolon(builder);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static PsiBuilder braceMatchingBuilder(final PsiBuilder builder) {
    return BasicJavaParserUtil.braceMatchingBuilder(builder);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static PsiBuilder stoppingBuilder(final PsiBuilder builder, final int stopAt) {
    return BasicJavaParserUtil.stoppingBuilder(builder, stopAt);
  }

  /**
   * @deprecated Use the new Java syntax library instead.
   *             See {@link com.intellij.java.syntax.parser.JavaParser}
   */
  @Deprecated
  public static PsiBuilder stoppingBuilder(final PsiBuilder builder, final Predicate<? super Pair<IElementType, String>> condition) {
    return BasicJavaParserUtil.stoppingBuilder(builder, condition);
  }
}
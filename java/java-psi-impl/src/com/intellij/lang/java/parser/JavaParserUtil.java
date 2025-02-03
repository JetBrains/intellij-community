// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterLazyParseableNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.WhitespacesAndCommentsBinder;
import com.intellij.lang.impl.TokenSequence;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.lexer.BasicJavaLexer;
import com.intellij.lang.java.lexer.JavaDocLexer;
import com.intellij.lexer.TokenList;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.WhiteSpaceAndCommentSetHolder;
import com.intellij.psi.impl.source.tree.ElementType;
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
  public static final TokenSet WS_COMMENTS = TokenSet.orSet(ElementType.JAVA_COMMENT_BIT_SET, TokenSet.WHITE_SPACE);

  public static @NotNull TokenList obtainTokens(@NotNull PsiFile file) {
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

  public static void setLanguageLevel(final PsiBuilder builder, final LanguageLevel level) {
    BasicJavaParserUtil.setLanguageLevel(builder, level);
  }

  public static @NotNull LanguageLevel getLanguageLevel(final PsiBuilder builder) {
    return BasicJavaParserUtil.getLanguageLevel(builder);
  }

  public static void setParseStatementCodeBlocksDeep(final PsiBuilder builder, final boolean deep) {
    BasicJavaParserUtil.setParseStatementCodeBlocksDeep(builder, deep);
  }

  public static boolean isParseStatementCodeBlocksDeep(final PsiBuilder builder) {
    return BasicJavaParserUtil.isParseStatementCodeBlocksDeep(builder);
  }

  public static @NotNull PsiBuilder createBuilder(final ASTNode chameleon) {
    return BasicJavaParserUtil.createBuilder(chameleon,
                                             (psi) -> PsiUtil.getLanguageLevel(psi),
                                             (level) -> (BasicJavaLexer)JavaParserDefinition.createLexer(level),
                                             (psi) -> obtainTokens(psi));
  }

  public static @NotNull PsiBuilder createBuilder(final LighterLazyParseableNode chameleon) {
    return BasicJavaParserUtil.createBuilder(chameleon,
                                             (psi) -> PsiUtil.getLanguageLevel(psi),
                                             (level) -> (BasicJavaLexer)JavaParserDefinition.createLexer(level));
  }

  public static @Nullable ASTNode parseFragment(final ASTNode chameleon, final ParserWrapper wrapper) {
    return BasicJavaParserUtil.parseFragment(chameleon, wrapper,
                                             (level) -> (JavaDocLexer)JavaParserDefinition.createDocLexer(level),
                                             (level) -> (BasicJavaLexer)JavaParserDefinition.createLexer(level)
    );
  }

  public static @Nullable ASTNode parseFragment(final ASTNode chameleon,
                                                final BasicJavaParserUtil.ParserWrapper wrapper,
                                                final boolean eatAll,
                                                final LanguageLevel level) {
    return BasicJavaParserUtil.parseFragment(chameleon, wrapper, eatAll, level,
                                             (levelLanguage) -> (JavaDocLexer)JavaParserDefinition.createDocLexer(levelLanguage),
                                             (levelLanguage) -> JavaParserDefinition.createLexer(levelLanguage)
    );
  }

  /**
   * @deprecated use {@link BasicJavaParserUtil#done(PsiBuilder.Marker, IElementType, PsiBuilder, WhiteSpaceAndCommentSetHolder)}
   */
  @Deprecated
  public static void done(final PsiBuilder.Marker marker, final IElementType type) {
    BasicJavaParserUtil.done(marker, type, WhiteSpaceAndCommentSetHolder.INSTANCE);
  }

  public static @Nullable IElementType exprType(final @Nullable PsiBuilder.Marker marker) {
    return BasicJavaParserUtil.exprType(marker);
  }

  // used instead of PsiBuilder.error() as it keeps all subsequent error messages
  public static void error(final PsiBuilder builder, @NotNull @NlsContexts.ParsingError String message) {
    BasicJavaParserUtil.error(builder, message);
  }

  public static void error(final PsiBuilder builder,
                           @NotNull @NlsContexts.ParsingError String message,
                           final @Nullable PsiBuilder.Marker before) {
    BasicJavaParserUtil.error(builder, message, before);
  }

  public static boolean expectOrError(PsiBuilder builder,
                                      TokenSet expected,
                                      @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String key) {
    return BasicJavaParserUtil.expectOrError(builder, expected, key);
  }

  public static boolean expectOrError(PsiBuilder builder,
                                      IElementType expected,
                                      @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String key) {
    return BasicJavaParserUtil.expectOrError(builder, expected, key);
  }

  public static void emptyElement(final PsiBuilder builder, final IElementType type) {
    BasicJavaParserUtil.emptyElement(builder, type);
  }

  public static void emptyElement(final PsiBuilder.Marker before, final IElementType type) {
    BasicJavaParserUtil.emptyElement(before, type);
  }

  public static void semicolon(final PsiBuilder builder) {
    BasicJavaParserUtil.semicolon(builder);
  }

  public static PsiBuilder braceMatchingBuilder(final PsiBuilder builder) {
    return BasicJavaParserUtil.braceMatchingBuilder(builder);
  }

  public static PsiBuilder stoppingBuilder(final PsiBuilder builder, final int stopAt) {
    return BasicJavaParserUtil.stoppingBuilder(builder, stopAt);
  }

  public static PsiBuilder stoppingBuilder(final PsiBuilder builder, final Predicate<? super Pair<IElementType, String>> condition) {
    return BasicJavaParserUtil.stoppingBuilder(builder, condition);
  }
}
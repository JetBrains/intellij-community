// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.java.syntax.element.SyntaxElementTypes;
import com.intellij.java.syntax.element.lazyParser.ParsingUtil;
import com.intellij.java.syntax.lexer.JavaLexer;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LighterLazyParseableNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.SyntaxElementTypeSet;
import com.intellij.platform.syntax.element.SyntaxTokenTypes;
import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.platform.syntax.lexer.TokenList;
import com.intellij.platform.syntax.lexer.TokenListUtil;
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder;
import com.intellij.platform.syntax.psi.ElementTypeConverter;
import com.intellij.platform.syntax.psi.ElementTypeConverterKt;
import com.intellij.platform.syntax.psi.ElementTypeConverters;
import com.intellij.platform.syntax.psi.IntelliJLogger;
import com.intellij.platform.syntax.psi.ParsingDiagnostics;
import com.intellij.platform.syntax.psi.PsiSyntaxBuilder;
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.indexing.IndexingDataKeys;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

import static com.intellij.platform.syntax.lexer.TokenListUtil.tokenListLexer;

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

  @FunctionalInterface
  public interface ParserWrapper {
    void parse(@NotNull SyntaxTreeBuilder builder, @NotNull LanguageLevel languageLevel);
  }


  private JavaParserUtil() { }

  public static @NotNull PsiSyntaxBuilderWithLanguageLevel createSyntaxBuilder(final ASTNode chameleon) {
    return createSyntaxBuilder(chameleon,
                               (psi) -> PsiUtil.getLanguageLevel(psi),
                               (psi) -> obtainTokens(psi));
  }

  public static @NotNull PsiSyntaxBuilderWithLanguageLevel createSyntaxBuilder(
    final ASTNode chameleon,
    @Nullable ElementTypeConverter additionalConverter) {
    return createSyntaxBuilder(chameleon,
                               (psi) -> PsiUtil.getLanguageLevel(psi),
                               (psi) -> obtainTokens(psi),
                               additionalConverter);
  }

  public static @Nullable ASTNode parseFragmentWithHighestLanguageLevel(
    @NotNull ASTNode chameleon,
    @NotNull JavaParserUtil.ParserWrapper wrapper,
    @NotNull Function<LanguageLevel, Lexer> lexer
  ) {
    return parseFragment(chameleon, wrapper, true, LanguageLevel.HIGHEST, lexer);
  }

  public static @Nullable ASTNode parseFragment(
    @NotNull ASTNode chameleon,
    @NotNull JavaParserUtil.ParserWrapper wrapper,
    boolean eatAll,
    @NotNull LanguageLevel level) {
    return parseFragment(chameleon, wrapper, eatAll, level, JavaLexer::new);
  }

  public static @Nullable ASTNode parseFragment(
    @NotNull ASTNode chameleon,
    @NotNull JavaParserUtil.ParserWrapper wrapper,
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
    return createSyntaxBuilder(chameleon, languageLevelFunction, psiAsLexer, null);
  }

  public static @NotNull PsiSyntaxBuilderWithLanguageLevel createSyntaxBuilder(@NotNull ASTNode chameleon,
                                                                               @NotNull Function<PsiElement, LanguageLevel> languageLevelFunction,
                                                                               @NotNull Function<PsiFile, TokenList> psiAsLexer,
                                                                               @Nullable ElementTypeConverter additionalConverter) {
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
    PsiSyntaxBuilder builder = factory.createBuilder(chameleon, lexer, language, text, additionalConverter);

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
}
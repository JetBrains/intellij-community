// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.impl;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PsiBuilderFactoryImpl extends PsiBuilderFactory {
  @NotNull
  @Override
  public PsiBuilder createBuilder(@NotNull final Project project, @NotNull final ASTNode chameleon) {
    return createBuilder(project, null, chameleon);
  }

  @NotNull
  @Override
  public PsiBuilder createBuilder(@NotNull final Project project, @NotNull final LighterLazyParseableNode chameleon) {
    final Language language = chameleon.getTokenType().getLanguage();
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);

    return new PsiBuilderImpl(project, parserDefinition, createLexer(project, language), chameleon, chameleon.getText());
  }

  @NotNull
  @Override
  public PsiBuilder createBuilder(@NotNull final Project project,
                                  @NotNull final ASTNode chameleon,
                                  @Nullable final Lexer lexer,
                                  @NotNull final Language lang,
                                  @NotNull final CharSequence seq) {
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
    return new PsiBuilderImpl(project, parserDefinition, lexer != null ? lexer : createLexer(project, lang), chameleon, seq);
  }

  @NotNull
  @Override
  public PsiBuilder createBuilder(@NotNull final Project project,
                                  @NotNull final LighterLazyParseableNode chameleon,
                                  @Nullable final Lexer lexer,
                                  @NotNull final Language lang,
                                  @NotNull final CharSequence seq) {
    final Language language = chameleon.getTokenType().getLanguage();
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
    return new PsiBuilderImpl(project, parserDefinition, lexer != null ? lexer : createLexer(project, lang), chameleon, seq);
  }

  private static Lexer createLexer(final Project project, final Language lang) {
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
    assert parserDefinition != null : "ParserDefinition absent for language: " + lang.getID();
    return parserDefinition.createLexer(project);
  }

  @NotNull
  @Override
  public PsiBuilder createBuilder(@NotNull final ParserDefinition parserDefinition,
                                  @NotNull final Lexer lexer,
                                  @NotNull final CharSequence seq) {
    return new PsiBuilderImpl(null, null, parserDefinition, lexer, null, seq, null, null);
  }

  @NotNull
  @Override
  public PsiBuilder createBuilder(@NotNull Project project,
                                  @NotNull final ParserDefinition parserDefinition,
                                  @NotNull final Lexer lexer,
                                  @NotNull final CharSequence seq) {
    return new PsiBuilderImpl(project, null, parserDefinition, lexer, null, seq, null, null);
  }
}

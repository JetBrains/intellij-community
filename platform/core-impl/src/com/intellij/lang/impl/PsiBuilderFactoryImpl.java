// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.impl;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PsiBuilderFactoryImpl extends PsiBuilderFactory {
  @Override
  public @NotNull PsiBuilder createBuilder(@NotNull Project project, @NotNull ASTNode chameleon) {
    return createBuilder(project, null, chameleon);
  }

  @Override
  public @NotNull PsiBuilder createBuilder(@NotNull Project project, @NotNull LighterLazyParseableNode chameleon) {
    ParserDefinition parserDefinition = getParserDefinition(null, chameleon.getTokenType());
    return new PsiBuilderImpl(project, parserDefinition, parserDefinition.createLexer(project), chameleon, chameleon.getText());
  }

  @Override
  public @NotNull PsiBuilder createBuilder(@NotNull Project project,
                                           @NotNull ASTNode chameleon,
                                           @Nullable Lexer lexer,
                                           @NotNull Language lang,
                                           @NotNull CharSequence seq) {
    ParserDefinition parserDefinition = getParserDefinition(lang, chameleon.getElementType());
    return new PsiBuilderImpl(project, parserDefinition, lexer != null ? lexer : parserDefinition.createLexer(project), chameleon, seq);
  }

  @Override
  public @NotNull PsiBuilder createBuilder(@NotNull Project project,
                                           @NotNull LighterLazyParseableNode chameleon,
                                           @Nullable Lexer lexer,
                                           @NotNull Language lang,
                                           @NotNull CharSequence seq) {
    ParserDefinition parserDefinition = getParserDefinition(null, chameleon.getTokenType());
    return new PsiBuilderImpl(project, parserDefinition, lexer != null ? lexer : parserDefinition.createLexer(project), chameleon, seq);
  }

  @Override
  public @NotNull PsiBuilder createBuilder(@NotNull ParserDefinition parserDefinition,
                                           @NotNull Lexer lexer,
                                           @NotNull CharSequence seq) {
    return new PsiBuilderImpl(null, null, parserDefinition, lexer, null, seq, null, null);
  }

  private static @NotNull ParserDefinition getParserDefinition(@Nullable Language language, @NotNull IElementType tokenType) {
    Language adjusted = language == null ? tokenType.getLanguage() : language;
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(adjusted);
    if (parserDefinition == null) {
      throw new AssertionError("ParserDefinition absent for language: '" + adjusted.getID() + "' (" + adjusted.getClass().getName() + "), " +
                               "for elementType: '" + tokenType.getDebugName() + "' (" + tokenType.getClass().getName() + ")");
    }
    return parserDefinition;
  }
}

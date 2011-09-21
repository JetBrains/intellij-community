/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
    return createBuilder(project, chameleon, null, chameleon.getElementType().getLanguage(), chameleon.getChars());
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
    return new PsiBuilderImpl(null, null, parserDefinition.getWhitespaceTokens(), parserDefinition.getCommentTokens(), lexer, null, seq, null, null);
  }
}

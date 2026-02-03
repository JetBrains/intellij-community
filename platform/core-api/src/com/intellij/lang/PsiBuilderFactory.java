// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PsiBuilderFactory {
  public static PsiBuilderFactory getInstance() {
    return ApplicationManager.getApplication().getService(PsiBuilderFactory.class);
  }

  public abstract @NotNull PsiBuilder createBuilder(@NotNull Project project, @NotNull ASTNode chameleon);

  public abstract @NotNull PsiBuilder createBuilder(@NotNull Project project, @NotNull LighterLazyParseableNode chameleon);

  public @NotNull PsiBuilder createBuilder(@NotNull Project project, @Nullable Lexer lexer, @NotNull ASTNode chameleon) {
    return createBuilder(project, chameleon, lexer, chameleon.getElementType().getLanguage(), chameleon.getChars());
  }

  public abstract @NotNull PsiBuilder createBuilder(@NotNull Project project,
                                                    @NotNull ASTNode chameleon,
                                                    @Nullable Lexer lexer,
                                                    @NotNull Language lang,
                                                    @NotNull CharSequence seq);

  public abstract @NotNull PsiBuilder createBuilder(@NotNull Project project,
                                                    @NotNull LighterLazyParseableNode chameleon,
                                                    @Nullable Lexer lexer,
                                                    @NotNull Language lang,
                                                    @NotNull CharSequence seq);

  public abstract @NotNull PsiBuilder createBuilder(@NotNull ParserDefinition parserDefinition, @NotNull Lexer lexer, @NotNull CharSequence seq);
}

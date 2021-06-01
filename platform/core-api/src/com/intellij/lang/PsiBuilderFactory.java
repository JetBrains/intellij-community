// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public abstract PsiBuilder createBuilder(@NotNull Project project, @NotNull ASTNode chameleon);

  @NotNull
  public abstract PsiBuilder createBuilder(@NotNull Project project, @NotNull LighterLazyParseableNode chameleon);

  @NotNull
  public PsiBuilder createBuilder(@NotNull Project project, @Nullable Lexer lexer, @NotNull ASTNode chameleon) {
    return createBuilder(project, chameleon, lexer, chameleon.getElementType().getLanguage(), chameleon.getChars());
  }

  @NotNull
  public abstract PsiBuilder createBuilder(@NotNull Project project,
                                           @NotNull ASTNode chameleon,
                                           @Nullable Lexer lexer,
                                           @NotNull Language lang,
                                           @NotNull CharSequence seq);

  @NotNull
  public abstract PsiBuilder createBuilder(@NotNull Project project,
                                           @NotNull LighterLazyParseableNode chameleon,
                                           @Nullable Lexer lexer,
                                           @NotNull Language lang,
                                           @NotNull CharSequence seq);

  @NotNull
  public abstract PsiBuilder createBuilder(@NotNull ParserDefinition parserDefinition, @NotNull Lexer lexer, @NotNull CharSequence seq);
}

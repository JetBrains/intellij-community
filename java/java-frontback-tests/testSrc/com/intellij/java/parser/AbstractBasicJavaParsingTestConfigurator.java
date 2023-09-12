// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiBuilder;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public interface AbstractBasicJavaParsingTestConfigurator {
  ParserDefinition getJavaParserDefinition();

  void setUp(@NotNull AbstractBasicJavaParsingTestCase thinJavaParsingTestCase);

  void configure(@NotNull PsiFile file);

  @NotNull
  PsiFile createPsiFile(@NotNull AbstractBasicJavaParsingTestCase thinJavaParsingTestCase,
                        @NotNull String name,
                        @NotNull String text,
                        @NotNull Consumer<PsiBuilder> parser);

  boolean checkPsi();

  void setLanguageLevel(@NotNull LanguageLevel level);
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import com.intellij.lang.java.parser.BasicJavaParserUtil;
import com.intellij.platform.syntax.tree.SyntaxNode;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AbstractBasicJavaParsingTestConfigurator {
  void setUp(@NotNull AbstractBasicJavaParsingTestCase thinJavaParsingTestCase);

  void configure(@NotNull PsiFile file);

  @NotNull
  PsiFile createPsiFile(@NotNull AbstractBasicJavaParsingTestCase thinJavaParsingTestCase,
                        @NotNull String name,
                        @NotNull String text,
                        @NotNull BasicJavaParserUtil.ParserWrapper parser);

  /**
   * @param text          text to parse
   * @param parserWrapper parser to use or `null` if we want to parse the whole file
   * @return SyntaxNode   node representing the parsed content
   */
  @NotNull
  SyntaxNode createFileSyntaxNode(@NotNull String text, @Nullable BasicJavaParserUtil.ParserWrapper parserWrapper);

  boolean checkPsi();

  void setLanguageLevel(@NotNull LanguageLevel level);

  @NotNull LanguageLevel getLanguageLevel();
}

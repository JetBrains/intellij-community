// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExpressionParser extends BasicExpressionParser {
  public ExpressionParser(@NotNull JavaParser javaParser) {
    super(javaParser, new OldExpressionParser(javaParser), new PrattExpressionParser(javaParser));
  }


  @Override
  public @Nullable PsiBuilder.Marker parseCaseLabel(@NotNull PsiBuilder builder) {
    return super.parseCaseLabel(builder);
  }
}
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.parser;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExpressionParser extends BasicExpressionParser {
  public ExpressionParser(@NotNull JavaParser javaParser) {
    super(javaParser, new OldExpressionParser(javaParser), new PrattExpressionParser(javaParser));
  }


  @Nullable
  @Override
  public PsiBuilder.Marker parseCaseLabel(@NotNull PsiBuilder builder) {
    return super.parseCaseLabel(builder);
  }
}
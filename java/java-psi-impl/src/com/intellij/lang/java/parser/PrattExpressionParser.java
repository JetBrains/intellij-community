// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PrattExpressionParser extends BasicPrattExpressionParser {
  public PrattExpressionParser(@NotNull JavaParser parser) {
    super(parser);
  }

  @Nullable
  @Override
  public PsiBuilder.Marker parse(@NotNull PsiBuilder builder) {
    return super.parse(builder);
  }

  @Nullable
  @Override
  public PsiBuilder.Marker parse(@NotNull PsiBuilder builder, int mode) {
    return super.parse(builder, mode);
  }
}

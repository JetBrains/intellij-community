// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PrattExpressionParser extends BasicPrattExpressionParser {
  public PrattExpressionParser(@NotNull JavaParser parser) {
    super(parser);
  }
}

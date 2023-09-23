// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StatementParser extends BasicStatementParser {

  public StatementParser(@NotNull JavaParser javaParser) {
    super(javaParser);
  }


  @Nullable
  @Override
  public PsiBuilder.Marker parseCodeBlock(@NotNull PsiBuilder builder) {
    return super.parseCodeBlock(builder);
  }

  @Override
  public void parseStatements(@NotNull PsiBuilder builder) {
    super.parseStatements(builder);
  }

  @NotNull
  @Override
  public PsiBuilder.Marker parseExprInParenthWithBlock(@NotNull PsiBuilder builder, @NotNull IElementType type, boolean block) {
    return super.parseExprInParenthWithBlock(builder, type, block);
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.pratt;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LangBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public abstract class PrattParser implements PsiParser {
  protected abstract PrattRegistry getRegistry();

  @Override
  public final @NotNull ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
    final PrattBuilder prattBuilder = PrattBuilderImpl.createBuilder(builder, getRegistry());
    final MutableMarker marker = prattBuilder.mark();
    parse(prattBuilder);
    marker.finish(root);
    return builder.getTreeBuilt();
  }

  protected void parse(final PrattBuilder builder) {
    builder.parse();
    if (!builder.isEof()) {
      builder.error(LangBundle.message("unexpected.token"));
      while (!builder.isEof()) builder.advance();
    }
  }
}

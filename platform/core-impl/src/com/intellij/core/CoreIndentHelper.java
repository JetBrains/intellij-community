// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.codeStyle.IndentHelper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The indent helper of an environment without a formatter. A PSI change records the indent of an element through the helper, and only
 * the formatter reads the record, so the helper reports no indent. A client with a formatter registers the helper of the IDE instead.
 */
@ApiStatus.Internal
public final class CoreIndentHelper extends IndentHelper {
  @Override
  public int getIndent(@NotNull PsiFile file, @NotNull ASTNode element) {
    return 0;
  }

  @Override
  public int getIndent(@NotNull PsiFile file, @NotNull ASTNode element, boolean includeNonSpace) {
    return 0;
  }
}

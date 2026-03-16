// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public final class PsiDocFormatString extends LeafPsiElement {
  public PsiDocFormatString(@NotNull IElementType type,
                            @NotNull CharSequence text) {
    super(type, text);
  }
}

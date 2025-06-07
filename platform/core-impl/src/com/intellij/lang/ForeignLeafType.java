// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.psi.impl.source.tree.ForeignLeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILeafElementType;
import org.jetbrains.annotations.NotNull;

public class ForeignLeafType extends TokenWrapper implements ILeafElementType {
  public ForeignLeafType(@NotNull IElementType delegate, @NotNull CharSequence value) {
    super(delegate, value);
  }

  @Override
  public @NotNull ASTNode createLeafNode(@NotNull CharSequence leafText) {
    return new ForeignLeafPsiElement(this, getText());
  }
}

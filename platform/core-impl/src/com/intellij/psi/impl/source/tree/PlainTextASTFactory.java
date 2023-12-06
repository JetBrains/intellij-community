// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTFactory;
import com.intellij.psi.PlainTextTokenTypes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlainTextASTFactory extends ASTFactory {

  @Override
  public @Nullable LeafElement createLeaf(@NotNull IElementType type, @NotNull CharSequence text) {
    if (type == PlainTextTokenTypes.PLAIN_TEXT) {
      return new PsiPlainTextImpl(text);
    }
    return null;
  }
}

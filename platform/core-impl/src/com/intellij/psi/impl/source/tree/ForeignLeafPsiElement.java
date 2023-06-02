// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ForeignLeafType;
import com.intellij.lang.TokenWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class ForeignLeafPsiElement extends LeafPsiElement {
  private final @NotNull ForeignLeafType myForeignType;

  public ForeignLeafPsiElement(@NotNull ForeignLeafType type, CharSequence text) {
    super(dereferenceElementType(type.getDelegate()), text);
    myForeignType = type;
  }

  private static @NotNull IElementType dereferenceElementType(@NotNull IElementType type) {
    while ( type instanceof TokenWrapper)
      type = (( TokenWrapper ) type ).getDelegate();

    return type;
  }

  @Override
  public LeafElement findLeafElementAt(int offset) {
    return null;
  }

  @Override
  public boolean textMatches(@NotNull CharSequence seq) {
    return false;
  }

  @Override
  protected int textMatches(@NotNull CharSequence buffer, int start) {
    return start;
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public int getTextLength() {
    return 0;
  }

  @Override
  public int getStartOffset() {
    return 0;
  }

  public @NotNull ForeignLeafType getForeignType() {
    return myForeignType;
  }

  @Override
  public String toString() {
    return "ForeignLeaf(" + getElementType() + ": " + getText() + ")";
  }
}

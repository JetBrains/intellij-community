// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public final class DummyBlockType {
  public static final IElementType DUMMY_BLOCK = new DummyBlockElementType();

  private static class DummyBlockElementType extends IElementType implements ICompositeElementType {
    DummyBlockElementType() {
      super("DUMMY_BLOCK", Language.ANY);
    }

    @Override
    public @NotNull ASTNode createCompositeNode() {
      return new DummyBlock();
    }
  }

  public static class DummyBlock extends CompositePsiElement {
    public DummyBlock() {
      super(DUMMY_BLOCK);
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
      return PsiReference.EMPTY_ARRAY;
    }

    @Override
    public @NotNull Language getLanguage() {
      return getParent().getLanguage();
    }
  }
}
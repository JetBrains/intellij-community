// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.PsiAnonymousClassImpl;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.PsiEnumConstantInitializerImpl;
import com.intellij.psi.impl.source.PsiImplicitClassImpl;
import com.intellij.psi.impl.source.tree.java.AnonymousClassElement;
import com.intellij.psi.impl.source.tree.java.EnumConstantInitializerElement;
import com.intellij.psi.impl.source.tree.java.ImplicitClassElement;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentProviderElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public abstract class JavaClassElementType extends JavaStubElementType implements ParentProviderElementType, ICompositeElementType, JavaNonCompositeElementType {
  private final IElementType myParentElementType;

  public JavaClassElementType(@NotNull String id, @NotNull IElementType type) {
    super(id);
    myParentElementType = type;
  }

  @Override
  public @NotNull Set<IElementType> getParents() {
    return Collections.singleton(myParentElementType);
  }

  @Override
  public PsiClass createPsi(final @NotNull ASTNode node) {
    if (node instanceof EnumConstantInitializerElement) {
      return new PsiEnumConstantInitializerImpl(node);
    }
    if (node instanceof AnonymousClassElement) {
      return new PsiAnonymousClassImpl(node);
    }
    if (node instanceof ImplicitClassElement) {
      return new PsiImplicitClassImpl(node);
    }

    return new PsiClassImpl(node);
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.tree.java.PsiTypeParameterListImpl;
import com.intellij.psi.impl.source.tree.java.TypeParameterListElement;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentProviderElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class JavaTypeParameterListElementType extends JavaStubElementType implements ICompositeElementType, ParentProviderElementType, JavaNonCompositeElementType {
  public JavaTypeParameterListElementType() {
    super("TYPE_PARAMETER_LIST", true);
  }

  @Override
  public @NotNull Set<IElementType> getParents() {
    return Collections.singleton(BasicJavaElementType.BASIC_TYPE_PARAMETER_LIST);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new TypeParameterListElement();
  }

  @Override
  public PsiTypeParameterList createPsi(final @NotNull ASTNode node) {
    return new PsiTypeParameterListImpl(node);
  }
}
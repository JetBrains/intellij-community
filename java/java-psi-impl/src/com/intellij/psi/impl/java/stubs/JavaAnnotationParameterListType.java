// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.impl.source.BasicJavaElementType;
import com.intellij.psi.impl.source.tree.java.AnnotationParamListElement;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationParamListImpl;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentProviderElementType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class JavaAnnotationParameterListType extends JavaStubElementType implements ICompositeElementType, ParentProviderElementType, JavaNonCompositeElementType {
  public JavaAnnotationParameterListType() {
    super("ANNOTATION_PARAMETER_LIST", true);
  }

  @Override
  public @NotNull Set<IElementType> getParents() {
    return Collections.singleton(BasicJavaElementType.BASIC_ANNOTATION_PARAMETER_LIST);
  }

  @Override
  public PsiAnnotationParameterList createPsi(@NotNull ASTNode node) {
    return new PsiAnnotationParamListImpl(node);
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return new AnnotationParamListElement();
  }
}
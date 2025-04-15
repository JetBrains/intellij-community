// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiAnnotationStubImpl;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaAnnotationStubFactory implements LightStubElementFactory<PsiAnnotationStubImpl, PsiAnnotation> {
  @Override
  public @NotNull PsiAnnotationStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    String text = LightTreeUtil.toFilteredString(tree, node, null);
    return new PsiAnnotationStubImpl(parentStub, text);
  }

  @Override
  public PsiAnnotation createPsi(@NotNull PsiAnnotationStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createAnnotation(stub);
  }
  
  @Override
  public @NotNull PsiAnnotationStubImpl createStub(@NotNull PsiAnnotation psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
}
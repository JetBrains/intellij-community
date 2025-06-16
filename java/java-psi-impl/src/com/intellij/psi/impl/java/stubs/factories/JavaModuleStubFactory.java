// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaModuleStubImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaModuleStubFactory implements LightStubElementFactory<PsiJavaModuleStubImpl, PsiJavaModule> {
  @Override
  public @NotNull PsiJavaModuleStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    LighterASTNode ref = LightTreeUtil.requiredChildOfType(tree, node, JavaElementType.MODULE_REFERENCE);
    return new PsiJavaModuleStubImpl(parentStub, JavaSourceUtil.getReferenceText(tree, ref), 0);
  }

  @Override
  public PsiJavaModule createPsi(@NotNull PsiJavaModuleStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createModule(stub);
  }
  
  @Override
  public @NotNull PsiJavaModuleStubImpl createStub(@NotNull PsiJavaModule psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
}
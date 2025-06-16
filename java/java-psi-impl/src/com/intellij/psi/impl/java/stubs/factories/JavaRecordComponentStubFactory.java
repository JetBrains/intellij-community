// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiRecordComponentStubImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaRecordComponentStubFactory implements LightStubElementFactory<PsiRecordComponentStubImpl, PsiRecordComponent> {
  @Override
  public @NotNull PsiRecordComponentStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    TypeInfo typeInfo = TypeInfo.create(tree, node, parentStub);
    LighterASTNode id = LightTreeUtil.requiredChildOfType(tree, node, JavaTokenType.IDENTIFIER);
    String name = RecordUtil.intern(tree.getCharTable(), id);

    LighterASTNode modifierList = LightTreeUtil.firstChildOfType(tree, node, JavaElementType.MODIFIER_LIST);
    boolean hasDeprecatedAnnotation = modifierList != null && RecordUtil.isDeprecatedByAnnotation(tree, modifierList);
    return new PsiRecordComponentStubImpl(parentStub, name, typeInfo, typeInfo.isEllipsis(), hasDeprecatedAnnotation);
  }

  @Override
  public PsiRecordComponent createPsi(@NotNull PsiRecordComponentStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createRecordComponent(stub);
  }
  
  @Override
  public @NotNull PsiRecordComponentStubImpl createStub(@NotNull PsiRecordComponent psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
}
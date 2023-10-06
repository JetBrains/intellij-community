// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiRecordHeaderStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClsRecordHeaderImpl extends ClsRepositoryPsiElement<PsiRecordHeaderStub> implements PsiRecordHeader {
  public ClsRecordHeaderImpl(@NotNull PsiRecordHeaderStub stub) {
    super(stub);
  }

  @Override
  public PsiRecordComponent @NotNull [] getRecordComponents() {
    return getStub().getChildrenByType(JavaStubElementTypes.RECORD_COMPONENT, PsiRecordComponent.ARRAY_FACTORY);
  }

  @Override
  public @Nullable PsiClass getContainingClass() {
    return (PsiClass)getParent();
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    buffer.append('(');
    PsiRecordComponent[] parameters = getRecordComponents();
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) buffer.append(", ");
      appendText(parameters[i], indentLevel, buffer);
    }
    buffer.append(')');
  }

  @Override
  public String getText() {
    StringBuilder buffer = new StringBuilder();
    appendMirrorText(0, buffer);
    return buffer.toString();
  }

  @Override
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);
    setMirrors(getRecordComponents(), SourceTreeToPsiMap.<PsiRecordHeader>treeToPsiNotNull(element).getRecordComponents());
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitRecordHeader(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiRecordHeader";
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiParameterListStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClsParameterListImpl extends ClsRepositoryPsiElement<PsiParameterListStub> implements PsiParameterList {
  public ClsParameterListImpl(@NotNull PsiParameterListStub stub) {
    super(stub);
  }

  @Override
  public PsiParameter @NotNull [] getParameters() {
    return getStub().getChildrenByType(JavaStubElementTypes.PARAMETER, PsiParameter.ARRAY_FACTORY);
  }

  @Override
  public int getParameterIndex(@NotNull PsiParameter parameter) {
    assert parameter.getParent() == this;
    return PsiImplUtil.getParameterIndex(parameter, this);
  }

  @Override
  public int getParametersCount() {
    // All children of ClsParameterListImpl are actually parameters, so no need to filter additionally
    return getStub().getChildrenStubs().size();
  }

  @Override
  public @Nullable PsiParameter getParameter(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("index is negative: " + index);
    }
    int count = 0;
    for (StubElement<?> child : getStub().getChildrenStubs()) {
      if (child.getStubType() == JavaStubElementTypes.PARAMETER) {
        if (count == index) return (PsiParameter)child.getPsi();
        count++;
      }
    }
    return null;
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    buffer.append('(');
    PsiParameter[] parameters = getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) buffer.append(", ");
      appendText(parameters[i], indentLevel, buffer);
    }
    buffer.append(')');
  }

  @Override
  public String getText() {
    StringBuilder builder = new StringBuilder();
    appendMirrorText(0, builder);
    return builder.toString();
  }

  @Override
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);
    setMirrors(getParameters(), SourceTreeToPsiMap.<PsiParameterList>treeToPsiNotNull(element).getParameters());
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiParameterList";
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiTypeParameterListStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;

public class ClsTypeParametersListImpl extends ClsRepositoryPsiElement<PsiTypeParameterListStub> implements PsiTypeParameterList {
  public ClsTypeParametersListImpl(@NotNull PsiTypeParameterListStub stub) {
    super(stub);
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    final PsiTypeParameter[] params = getTypeParameters();
    if (params.length != 0) {
      buffer.append('<');
      for (int i = 0; i < params.length; i++) {
        if (i > 0) buffer.append(", ");
        appendText(params[i], indentLevel, buffer);
      }
      buffer.append(">");
    }
  }

  @Override
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);
    setMirrors(getTypeParameters(), SourceTreeToPsiMap.<PsiTypeParameterList>treeToPsiNotNull(element).getTypeParameters());
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiTypeParameter @NotNull [] getTypeParameters() {
    return getStub().getChildrenByType(JavaStubElementTypes.TYPE_PARAMETER, PsiTypeParameter.ARRAY_FACTORY);
  }

  @Override
  public int getTypeParameterIndex(@NotNull PsiTypeParameter typeParameter) {
    assert typeParameter.getParent() == this;
    return PsiImplUtil.getTypeParameterIndex(typeParameter, this);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    final PsiTypeParameter[] typeParameters = getTypeParameters();
    for (PsiTypeParameter parameter : typeParameters) {
      if (!processor.execute(parameter, state)) return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "PsiTypeParameterList";
  }
}

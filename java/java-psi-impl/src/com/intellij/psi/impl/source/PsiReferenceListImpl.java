// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaClassReferenceListElementType;
import com.intellij.psi.impl.java.stubs.PsiClassReferenceListStub;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NotNull;

public class PsiReferenceListImpl extends JavaStubPsiElement<PsiClassReferenceListStub> implements PsiReferenceList {
  public PsiReferenceListImpl(@NotNull PsiClassReferenceListStub stub) {
    super(stub, stub.getStubType());
  }

  public PsiReferenceListImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiJavaCodeReferenceElement @NotNull [] getReferenceElements() {
    return calcTreeElement().getChildrenAsPsiElements(JavaElementType.JAVA_CODE_REFERENCE, PsiJavaCodeReferenceElement.ARRAY_FACTORY);
  }

  @Override
  public PsiClassType @NotNull [] getReferencedTypes() {
    PsiClassReferenceListStub stub = getGreenStub();
    if (stub != null) {
      return stub.getReferencedTypes();
    }

    PsiJavaCodeReferenceElement[] refs = getReferenceElements();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    PsiClassType[] types = refs.length == 0 ? PsiClassType.EMPTY_ARRAY : new PsiClassType[refs.length];
    for (int i = 0; i < types.length; i++) {
      types[i] = factory.createType(refs[i]);
    }

    return types;
  }

  @Override
  public Role getRole() {
    return JavaClassReferenceListElementType.elementTypeToRole(getElementType());
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReferenceList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiReferenceList";
  }
}
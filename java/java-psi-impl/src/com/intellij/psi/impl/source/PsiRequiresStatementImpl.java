// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiRequiresStatementStub;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.SoftReference;

import static com.intellij.openapi.util.text.StringUtil.nullize;
import static com.intellij.reference.SoftReference.dereference;

public class PsiRequiresStatementImpl extends JavaStubPsiElement<PsiRequiresStatementStub> implements PsiRequiresStatement {
  private SoftReference<PsiJavaModuleReference> myReference;

  public PsiRequiresStatementImpl(@NotNull PsiRequiresStatementStub stub) {
    super(stub, JavaStubElementTypes.REQUIRES_STATEMENT);
  }

  public PsiRequiresStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiJavaModuleReferenceElement getReferenceElement() {
    return PsiTreeUtil.getChildOfType(this, PsiJavaModuleReferenceElement.class);
  }

  @Override
  public String getModuleName() {
    PsiRequiresStatementStub stub = getGreenStub();
    if (stub != null) {
      return nullize(stub.getModuleName());
    }
    else {
      PsiJavaModuleReferenceElement refElement = getReferenceElement();
      return refElement != null ? refElement.getReferenceText() : null;
    }
  }

  @Override
  public PsiJavaModuleReference getModuleReference() {
    PsiRequiresStatementStub stub = getStub();
    if (stub != null) {
      String refText = nullize(stub.getModuleName());
      if (refText == null) return null;
      PsiJavaModuleReference ref = dereference(myReference);
      if (ref == null) {
        ref = JavaPsiFacade.getInstance(getProject()).getParserFacade().createModuleReferenceFromText(refText, this).getReference();
        myReference = new SoftReference<>(ref);
      }
      return ref;
    }
    else {
      myReference = null;
      PsiJavaModuleReferenceElement refElement = getReferenceElement();
      return refElement != null ? refElement.getReference() : null;
    }
  }

  @Override
  public PsiModifierList getModifierList() {
    return getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST, PsiModifierList.class);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitRequiresStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiRequiresStatement";
  }
}
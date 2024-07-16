// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.light.LightClassReference;
import org.jetbrains.annotations.NotNull;

public class PsiEnumConstantInitializerImpl extends PsiClassImpl implements PsiEnumConstantInitializer {
  private static final Logger LOG = Logger.getInstance(PsiEnumConstantInitializerImpl.class);
  private PsiClassType myCachedBaseType;

  public PsiEnumConstantInitializerImpl(PsiClassStub stub) {
    super(stub, JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER);
  }

  public PsiEnumConstantInitializerImpl(ASTNode node) {
    super(node);
  }

  @Override
  protected Object clone() {
    PsiEnumConstantInitializerImpl clone = (PsiEnumConstantInitializerImpl)super.clone();
    clone.myCachedBaseType = null;
    return clone;
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myCachedBaseType = null;
  }

  @Override
  public PsiExpressionList getArgumentList() {
    PsiElement parent = getParent();
    LOG.assertTrue(parent instanceof PsiEnumConstant);
    return ((PsiCall)parent).getArgumentList();
  }

  @Override
  public boolean isInQualifiedNew() {
    return false;
  }

  @Override
  public @NotNull PsiJavaCodeReferenceElement getBaseClassReference() {
    PsiClass containingClass = getBaseClass();
    return new LightClassReference(getManager(), containingClass.getName(), containingClass);
  }

  private PsiClass getBaseClass() {
    PsiElement parent = getParent();
    LOG.assertTrue(parent instanceof PsiEnumConstant, parent);
    PsiClass containingClass = ((PsiEnumConstant)parent).getContainingClass();
    LOG.assertTrue(containingClass != null);
    return containingClass;
  }

  @Override
  public @NotNull PsiEnumConstant getEnumConstant() {
    return (PsiEnumConstant) getParent();
  }

  @Override
  public @NotNull PsiClassType getBaseClassType() {
    PsiClassType cachedBaseType = myCachedBaseType;
    if (cachedBaseType == null) {
      myCachedBaseType = cachedBaseType = JavaPsiFacade.getElementFactory(getProject()).createType(getBaseClass());
    }
    return cachedBaseType;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public String getQualifiedName() {
    return null;
  }

  @Override
  public PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return false;
  }

  @Override
  public PsiReferenceList getExtendsList() {
    return null;
  }

  @Override
  public PsiReferenceList getImplementsList() {
    return null;
  }

  @Override
  public PsiClassType @NotNull [] getSuperTypes() {
    return new PsiClassType[]{getBaseClassType()};
  }

  @Override
  public boolean isInterface() {
    return false;
  }

  @Override
  public boolean isAnnotationType() {
    return false;
  }

  @Override
  public boolean isEnum() {
    return false;
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return null;
  }

  @Override
  public PsiElement getOriginalElement() {
    return this;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitEnumConstantInitializer(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiAnonymousClass (PsiEnumConstantInitializerImpl)):";
  }
}
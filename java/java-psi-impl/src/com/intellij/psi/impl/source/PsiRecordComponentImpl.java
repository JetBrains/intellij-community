// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiRecordComponentStub;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;

public class PsiRecordComponentImpl extends JavaStubPsiElement<PsiRecordComponentStub> implements PsiRecordComponent {

  private volatile Reference<PsiType> myCachedType;

  public PsiRecordComponentImpl(@NotNull PsiRecordComponentStub stub) {
    super(stub, JavaStubElementTypes.RECORD_COMPONENT);
  }

  public PsiRecordComponentImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitRecordComponent(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    if (parent == null) return null;
    PsiElement grandParent = parent.getParent();
    return grandParent instanceof PsiClass ? (PsiClass)grandParent : null;
  }

  @NotNull
  @Override
  public PsiModifierList getModifierList() {
    final PsiModifierList modifierList = getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
    assert modifierList != null : this;
    return modifierList;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @NotNull
  @Override
  public PsiType getType() {
    PsiRecordComponentStub stub = getStub();
    if (stub != null) {
      PsiType type = SoftReference.dereference(myCachedType);
      if (type == null) {
        type = JavaSharedImplUtil.createTypeFromStub(this, stub.getType());
        myCachedType = new SoftReference<>(type);
      }
      return type;
    }

    myCachedType = null;

    PsiTypeElement typeElement = getTypeElement();
    return JavaSharedImplUtil.getType(typeElement, getNameIdentifier());
  }

  @NotNull
  @Override
  public PsiTypeElement getTypeElement() {
    return findNotNullChildByType(JavaElementType.TYPE);
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    JavaSharedImplUtil.normalizeBrackets(this);
  }

  @Nullable
  @Override
  public Object computeConstantValue() {
    return null;
  }

  @NotNull
  @Override
  public PsiIdentifier getNameIdentifier() {
    return findNotNullChildByType(JavaTokenType.IDENTIFIER);
  }

  @Override
  public int getTextOffset() {
    return getNameIdentifier().getTextOffset();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiIdentifier identifier = getNameIdentifier();
    return PsiImplUtil.setName(identifier, name);
  }

  @NotNull
  @Override
  public String getName() {
    final PsiRecordComponentStub stub = getGreenStub();
    if (stub != null) {
      return stub.getName();
    }
    return getNameIdentifier().getText();
  }

  @Override
  @NotNull
  public CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  @Nullable
  @Override
  public PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return false;
  }

  @Override
  public boolean isVarArgs() {
    PsiRecordComponentStub stub = getGreenStub();
    if (stub != null) {
      return stub.isVararg();
    }
    return getType() instanceof PsiEllipsisType;
  }

  @Override
  public String toString() {
    return "PsiRecordComponent:" + getName();
  }
}

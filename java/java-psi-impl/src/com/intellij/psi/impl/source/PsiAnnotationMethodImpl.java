// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PsiAnnotationMethodImpl extends PsiMethodImpl implements PsiAnnotationMethod {
  private SoftReference<PsiAnnotationMemberValue> myCachedDefaultValue;

  public PsiAnnotationMethodImpl(PsiMethodStub stub) {
    super(stub, JavaStubElementTypes.ANNOTATION_METHOD);
  }

  public PsiAnnotationMethodImpl(ASTNode node) {
    super(node);
  }

  @Override
  protected void dropCached() {
    myCachedDefaultValue = null;
  }

  @Override
  public PsiAnnotationMemberValue getDefaultValue() {
    PsiMethodStub stub = getStub();
    if (stub != null) {
      String text = stub.getDefaultValueText();
      if (StringUtil.isEmpty(text)) return null;

      PsiAnnotationMemberValue value = SoftReference.dereference(myCachedDefaultValue);
      if (value != null) {
        return value;
      }

      value = JavaPsiFacade.getElementFactory(getProject()).createAnnotationFromText("@Foo(" + text + ")", this).findAttributeValue(null);
      myCachedDefaultValue = new SoftReference<>(value);
      return value;
    }

    myCachedDefaultValue = null;

    boolean expectedDefault = false;
    TreeElement childNode = getNode().getFirstChildNode();
    while (childNode != null) {
      IElementType type = childNode.getElementType();
      if (type == JavaTokenType.DEFAULT_KEYWORD) {
        expectedDefault = true;
      }
      else if (expectedDefault && ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET.contains(type)) {
        return (PsiAnnotationMemberValue)childNode.getPsi();
      }

      childNode = childNode.getTreeNext();
    }
    return null;
  }

  @Override
  @NonNls
  public String toString() {
    return "PsiAnnotationMethod:" + getName();
  }

  @Override
  public final void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotationMethod(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}
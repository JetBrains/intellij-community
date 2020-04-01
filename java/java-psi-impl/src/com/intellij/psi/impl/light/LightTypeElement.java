// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.light;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LightTypeElement extends LightElement implements PsiTypeElement {
  private final PsiType myType;

  public LightTypeElement(PsiManager manager, PsiType type) {
    super(manager, JavaLanguage.INSTANCE);
    type = PsiUtil.convertAnonymousToBaseType(type);
    myType = type;
  }

  @Override
  public String toString() {
    return "PsiTypeElement:" + getText();
  }

  @Override
  public String getText() {
    return myType.getPresentableText(true);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiElement copy() {
    return new LightTypeElement(myManager, myType);
  }

  @Override
  @NotNull
  public PsiType getType() {
    return myType;
  }

  @Override
  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    return null;
  }

  @Override
  public boolean isValid() {
    return myType.isValid();
  }

  @Override
  public PsiAnnotation @NotNull [] getAnnotations() {
    return myType.getAnnotations();
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return myType.findAnnotation(qualifiedName);
  }

  @Override
  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiAnnotation @NotNull [] getApplicableAnnotations() {
    return getAnnotations();
  }

}

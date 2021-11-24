// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.reference;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;

import java.util.Objects;

public class RefImplicitConstructorImpl extends RefMethodImpl implements RefImplicitConstructor {
  RefImplicitConstructorImpl(@NotNull RefClass ownerClass) {
    super(JavaAnalysisBundle.message("inspection.reference.implicit.constructor.name", ownerClass.getName()), ownerClass);
    setInitialized(true);
  }

  @Override
  public @NotNull RefClass getOwnerClass() {
    return Objects.requireNonNull(super.getOwnerClass());
  }

  @Override
  public void buildReferences() {
    getRefManager().fireBuildReferences(this);
  }

  @Override
  public boolean isSuspicious() {
    return ((RefClassImpl)getOwnerClass()).isSuspicious();
  }

  @Override
  public String getExternalName() {
    return getOwnerClass().getExternalName();
  }

  @Override
  public boolean isValid() {
    return ReadAction.compute(getOwnerClass()::isValid).booleanValue();
  }

  @NotNull
  @Override
  public String getAccessModifier() {
    return getOwnerClass().getAccessModifier();
  }

  @Override
  public void setAccessModifier(String am) {
    RefJavaUtil.getInstance().setAccessModifier(getOwnerClass(), am);
  }

  @Override
  public PsiModifierListOwner getElement() {
    return Objects.requireNonNull(getOwnerClass()).getElement();
  }

  @Override
  public UClass getUastElement() {
    return (UClass)super.getUastElement();
  }

  @Nullable
  @Override
  public PsiElement getPsiElement() {
    return getOwnerClass().getPsiElement();
  }

  @Override
  @Nullable
  public PsiFile getContainingFile() {
    return ((RefClassImpl)getOwnerClass()).getContainingFile();
  }

  @Override
  protected void initialize() {
    throw new AssertionError("Should not be called!");
  }
}

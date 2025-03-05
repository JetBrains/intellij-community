// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.reference;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class RefImplicitConstructorImpl extends RefMethodImpl implements RefImplicitConstructor {
  RefImplicitConstructorImpl(@NotNull RefClass ownerClass) {
    super(JavaAnalysisBundle.message("inspection.reference.implicit.constructor.name", ownerClass.getName()), ownerClass);
    setInitialized(true);
  }

  @Override
  public @NotNull RefClass getOwnerClass() {
    return Objects.requireNonNull(super.getOwnerClass());
  }

  @Override
  public void buildReferences() {}

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
    return getOwnerClass().isValid();
  }

  @Override
  public synchronized @NotNull String getAccessModifier() {
    return getOwnerClass().getAccessModifier();
  }

  @Override
  public void setAccessModifier(String am) {
    RefJavaUtil.getInstance().setAccessModifier(getOwnerClass(), am);
  }

  @Override
  public @Nullable PsiElement getPsiElement() {
    return getOwnerClass().getPsiElement();
  }

  @Override
  public @Nullable PsiFile getContainingFile() {
    return ((RefClassImpl)getOwnerClass()).getContainingFile();
  }

  @Override
  protected synchronized void initialize() {
    throw new AssertionError("Should not be called!");
  }
}

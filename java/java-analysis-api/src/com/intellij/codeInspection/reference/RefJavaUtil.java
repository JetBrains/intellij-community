// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.reference;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UDeclaration;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UMethod;

public abstract class RefJavaUtil {
  @Deprecated
  public abstract void addReferences(@NotNull PsiModifierListOwner psiFrom, @NotNull RefJavaElement ref, @Nullable PsiElement findIn);

  public void addReferencesTo(@NotNull UDeclaration decl, @NotNull RefJavaElement ref, @Nullable UElement... findIn) {
    throw new UnsupportedOperationException("Should be implemented");
  }

  public abstract RefClass getTopLevelClass(@NotNull RefElement refElement);

  public abstract boolean isInheritor(@NotNull RefClass subClass, RefClass superClass);

  @Nullable //default package name
  public abstract String getPackageName(RefEntity refEntity);

  @Nullable
  public RefClass getOwnerClass(RefManager refManager, UElement uElement) {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  @Nullable
  public RefClass getOwnerClass(RefManager refManager, PsiElement psiElement) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public abstract RefClass getOwnerClass(RefElement refElement);

  public abstract int compareAccess(String a1, String a2);

  @NotNull
  public abstract String getAccessModifier(@NotNull PsiModifierListOwner modifiersOwner);

  public abstract void setAccessModifier(@NotNull RefJavaElement refElement, @NotNull String newAccess);

  public abstract void setIsStatic(RefJavaElement refElement, boolean isStatic);

  public abstract void setIsFinal(RefJavaElement refElement, boolean isFinal);

  public boolean isMethodOnlyCallsSuper(UMethod derivedMethod) {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  public boolean isMethodOnlyCallsSuper(PsiMethod derivedMethod) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public static RefPackage getPackage(RefEntity refEntity) {
    while (refEntity != null && !(refEntity instanceof RefPackage)) refEntity = refEntity.getOwner();

    return (RefPackage)refEntity;
  }

  public static RefJavaUtil getInstance() {
    return ServiceManager.getService(RefJavaUtil.class);
  }

  public boolean isCallToSuperMethod(UExpression expression, UMethod method) {
    throw new UnsupportedOperationException();
  }

  public void addTypeReference(UElement uElement, PsiType psiType, RefManager refManager) {
    throw new UnsupportedOperationException();
  }

  public void addTypeReference(UElement uElement, PsiType psiType, RefManager refManager, @Nullable RefJavaElement refElement) {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  public boolean isCallToSuperMethod(PsiExpression expression, PsiMethod method) {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  public void addTypeReference(PsiElement psiElement, PsiType psiType, RefManager refManager) {
    throw new UnsupportedOperationException();
  }

  @Deprecated
  public void addTypeReference(PsiElement psiElement, PsiType psiType, RefManager refManager, @Nullable RefJavaElement refElement) {
    throw new UnsupportedOperationException();
  }
}

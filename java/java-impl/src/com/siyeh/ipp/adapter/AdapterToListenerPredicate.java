// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.adapter;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;

class AdapterToListenerPredicate implements PsiElementPredicate {

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiJavaCodeReferenceElement)) {
      return false;
    }
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiReferenceList referenceList)) {
      return false;
    }
    if (PsiReferenceList.Role.EXTENDS_LIST != referenceList.getRole()) {
      return false;
    }
    final PsiElement grandParent = referenceList.getParent();
    if (!(grandParent instanceof PsiClass)) {
      return false;
    }
    final PsiJavaCodeReferenceElement[] referenceElements =
      referenceList.getReferenceElements();
    if (referenceElements.length != 1) {
      return false;
    }
    final PsiJavaCodeReferenceElement referenceElement =
      referenceElements[0];
    final PsiElement target = referenceElement.resolve();
    if (!(target instanceof PsiClass aClass)) {
      return false;
    }
    @NonNls final String className = aClass.getName();
    if (!className.endsWith("Adapter")) {
      return false;
    }
    if (!aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return false;
    }
    final PsiReferenceList implementsList = aClass.getImplementsList();
    if (implementsList == null) {
      return false;
    }
    final PsiJavaCodeReferenceElement[] implementsReferences = implementsList.getReferenceElements();
    for (PsiJavaCodeReferenceElement implementsReference : implementsReferences) {
      @NonNls final String name = implementsReference.getReferenceName();
      if (name == null || !name.endsWith("Listener")) {
        continue;
      }
      final PsiElement implementsTarget = implementsReference.resolve();
      if (!(implementsTarget instanceof PsiClass implementsClass)) {
        continue;
      }
      if (!implementsClass.isInterface()) {
        continue;
      }
      return true;
    }
    return false;
  }
}
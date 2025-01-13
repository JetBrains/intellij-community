// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

final class GenericsChecker {
  private final @NotNull JavaErrorVisitor myVisitor;

  GenericsChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkElementInTypeParameterExtendsList(@NotNull PsiReferenceList referenceList,
                                              @NotNull PsiTypeParameter typeParameter,
                                              @NotNull JavaResolveResult resolveResult,
                                              @NotNull PsiJavaCodeReferenceElement element) {
    PsiJavaCodeReferenceElement[] referenceElements = referenceList.getReferenceElements();
    PsiClass extendFrom = (PsiClass)resolveResult.getElement();
    if (extendFrom == null) return;
    if (!extendFrom.isInterface() && referenceElements.length != 0 && element != referenceElements[0]) {
      myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_EXTENDS_INTERFACE_EXPECTED.create(element, typeParameter));
    }
    else if (referenceElements.length != 0 &&
             element != referenceElements[0] &&
             referenceElements[0].resolve() instanceof PsiTypeParameter) {
      myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_CANNOT_BE_FOLLOWED_BY_OTHER_BOUNDS.create(element, typeParameter));
    }
  }

  void checkCannotInheritFromTypeParameter(@Nullable PsiClass superClass, @NotNull PsiJavaCodeReferenceElement toHighlight) {
    if (superClass instanceof PsiTypeParameter) {
      myVisitor.report(JavaErrorKinds.CLASS_INHERITS_TYPE_PARAMETER.create(toHighlight, superClass));
    }
  }

  void checkTypeParametersList(@NotNull PsiTypeParameterList list, PsiTypeParameter @NotNull [] parameters) {
    PsiElement parent = list.getParent();
    if (parent instanceof PsiClass psiClass && psiClass.isEnum()) {
      myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_ON_ENUM.create(list));
      return;
    }
    if (PsiUtil.isAnnotationMethod(parent)) {
      myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_ON_ANNOTATION_MEMBER.create(list));
      return;
    }
    if (parent instanceof PsiClass psiClass && psiClass.isAnnotationType()) {
      myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_ON_ANNOTATION.create(list));
      return;
    }

    for (int i = 0; i < parameters.length; i++) {
      PsiTypeParameter typeParameter1 = parameters[i];
      myVisitor.myClassChecker.checkCyclicInheritance(typeParameter1);
      if (myVisitor.hasErrorResults()) return;
      String name1 = typeParameter1.getName();
      for (int j = i + 1; j < parameters.length; j++) {
        PsiTypeParameter typeParameter2 = parameters[j];
        String name2 = typeParameter2.getName();
        if (Objects.equals(name1, name2)) {
          myVisitor.report(JavaErrorKinds.TYPE_PARAMETER_DUPLICATE.create(typeParameter2));
        }
      }
    }
  }
}

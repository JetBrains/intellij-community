// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
}

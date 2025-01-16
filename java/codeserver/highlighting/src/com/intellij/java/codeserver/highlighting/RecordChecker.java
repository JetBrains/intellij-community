// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class RecordChecker {
  private final @NotNull JavaErrorVisitor myVisitor;

  RecordChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkRecordHeader(@NotNull PsiClass psiClass) {
    PsiRecordHeader header = psiClass.getRecordHeader();
    if (!psiClass.isRecord()) {
      if (header != null) {
        myVisitor.report(JavaErrorKinds.RECORD_HEADER_REGULAR_CLASS.create(header));
      }
      return;
    }
    PsiIdentifier identifier = psiClass.getNameIdentifier();
    if (identifier == null) return;
    if (header == null) {
      myVisitor.report(JavaErrorKinds.RECORD_NO_HEADER.create(psiClass));
    }
  }

  void checkRecordComponentWellFormed(@NotNull PsiRecordComponent component) {
    if (component.isVarArgs() && PsiTreeUtil.getNextSiblingOfType(component, PsiRecordComponent.class) != null) {
      myVisitor.report(JavaErrorKinds.RECORD_COMPONENT_VARARG_NOT_LAST.create(component));
      return;
    }
    TextRange range = MethodChecker.getCStyleDeclarationRange(component);
    if (range != null) {
      myVisitor.report(JavaErrorKinds.RECORD_COMPONENT_CSTYLE_DECLARATION.create(component, range));
      return;
    }
    PsiIdentifier identifier = component.getNameIdentifier();
    if (identifier != null && JavaPsiRecordUtil.ILLEGAL_RECORD_COMPONENT_NAMES.contains(identifier.getText())) {
      myVisitor.report(JavaErrorKinds.RECORD_COMPONENT_RESTRICTED_NAME.create(component));
    }
  }

  void checkRecordAccessorReturnType(@NotNull PsiRecordComponent component) {
    String componentName = component.getName();
    PsiTypeElement typeElement = component.getTypeElement();
    if (typeElement == null) return;
    PsiClass containingClass = component.getContainingClass();
    if (containingClass == null) return;
    PsiMethod[] methods = containingClass.findMethodsByName(componentName, false);
    for (PsiMethod method : methods) {
      if (method instanceof LightRecordMethod) {
        List<HierarchicalMethodSignature> superSignatures =
          PsiSuperMethodImplUtil.getHierarchicalMethodSignature(method, method.getResolveScope()).getSuperSignatures();
        MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
        myVisitor.myMethodChecker.checkMethodIncompatibleReturnType(component, signature, superSignatures);
      }
    }
  }
}

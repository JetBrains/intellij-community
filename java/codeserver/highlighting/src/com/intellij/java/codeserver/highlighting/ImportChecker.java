// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaErrorKind;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

final class ImportChecker {
  private final @NotNull JavaErrorVisitor myVisitor;
  private final Map<String, Pair<PsiImportStaticReferenceElement, PsiClass>> mySingleImportedClasses = new HashMap<>();
  private final Map<String, Pair<PsiImportStaticReferenceElement, PsiField>> mySingleImportedFields = new HashMap<>();

  ImportChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkImportStaticReferenceElement(@NotNull PsiImportStaticReferenceElement ref) {
    String refName = ref.getReferenceName();
    JavaResolveResult[] results = ref.multiResolve(false);

    PsiElement referenceNameElement = ref.getReferenceNameElement();
    if (results.length == 0) {
      assert referenceNameElement != null : ref;
      if (IncompleteModelUtil.isIncompleteModel(ref) && ref.getClassReference().resolve() == null) {
        myVisitor.report(JavaErrorKinds.REFERENCE_PENDING.create(referenceNameElement));
      } else {
        myVisitor.report(JavaErrorKinds.REFERENCE_UNRESOLVED.create(ref));
      }
    }
    else {
      PsiManager manager = ref.getManager();
      for (JavaResolveResult result : results) {
        PsiElement element = result.getElement();

        JavaErrorKind.Simple<PsiJavaCodeReferenceElement> kind = null;
        if (element instanceof PsiClass) {
          Pair<PsiImportStaticReferenceElement, PsiClass> imported = mySingleImportedClasses.get(refName);
          PsiClass aClass = Pair.getSecond(imported);
          if (aClass != null && !manager.areElementsEquivalent(aClass, element)) {
            kind = imported.first == null
                          ? JavaErrorKinds.IMPORT_SINGLE_CLASS_CONFLICT
                          : imported.first.equals(ref)
                            ? JavaErrorKinds.IMPORT_SINGLE_STATIC_CLASS_AMBIGUOUS
                            : JavaErrorKinds.IMPORT_SINGLE_STATIC_CLASS_ALREADY_DEFINED;
          }
          mySingleImportedClasses.put(refName, Pair.create(ref, (PsiClass)element));
        }
        else if (element instanceof PsiField) {
          Pair<PsiImportStaticReferenceElement, PsiField> imported = mySingleImportedFields.get(refName);
          PsiField field = Pair.getSecond(imported);
          if (field != null && !manager.areElementsEquivalent(field, element)) {
            kind = imported.first.equals(ref)
                          ? JavaErrorKinds.IMPORT_SINGLE_STATIC_FIELD_AMBIGUOUS
                          : JavaErrorKinds.IMPORT_SINGLE_STATIC_FIELD_ALREADY_DEFINED;
          }
          mySingleImportedFields.put(refName, Pair.create(ref, (PsiField)element));
        }

        if (kind != null) {
          myVisitor.report(kind.create(ref));
        }
      }
    }
    if (!myVisitor.hasErrorResults() && results.length == 1) {
      myVisitor.myExpressionChecker.checkReference(ref, results[0]);
      if (!myVisitor.hasErrorResults()) {
        PsiElement element = results[0].getElement();
        PsiClass containingClass = element instanceof PsiMethod psiMethod ? psiMethod.getContainingClass() : null;
        if (containingClass != null && containingClass.isInterface()) {
          myVisitor.myExpressionChecker.checkStaticInterfaceCallQualifier(ref, results[0], containingClass);
        }
      }
    }

  }
}

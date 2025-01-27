// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

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

  void checkStaticOnDemandImportResolvesToClass(@NotNull PsiImportStaticStatement statement) {
    if (statement.isOnDemand() && statement.resolveTargetClass() == null) {
      PsiJavaCodeReferenceElement ref = statement.getImportReference();
      if (ref != null) {
        PsiElement resolve = ref.resolve();
        if (resolve != null) {
          myVisitor.report(JavaErrorKinds.IMPORT_STATIC_ON_DEMAND_RESOLVES_TO_CLASS.create(ref));
        }
      }
    }
  }

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

        if (element instanceof PsiClass) {
          Pair<PsiImportStaticReferenceElement, PsiClass> imported = mySingleImportedClasses.get(refName);
          PsiClass aClass = Pair.getSecond(imported);
          if (aClass != null && !manager.areElementsEquivalent(aClass, element)) {
            if (imported.first == null) {
              myVisitor.report(JavaErrorKinds.IMPORT_SINGLE_CLASS_CONFLICT.create(ref, aClass));
            }
            else {
              var kind = imported.first.equals(ref)
                         ? JavaErrorKinds.IMPORT_SINGLE_STATIC_CLASS_AMBIGUOUS
                         : JavaErrorKinds.IMPORT_SINGLE_STATIC_CLASS_ALREADY_DEFINED;
              myVisitor.report(kind.create(ref));
            }
          }
          mySingleImportedClasses.put(refName, Pair.create(ref, (PsiClass)element));
        }
        else {
          if (element instanceof PsiField) {
            Pair<PsiImportStaticReferenceElement, PsiField> imported = mySingleImportedFields.get(refName);
            PsiField field = Pair.getSecond(imported);
            if (field != null && !manager.areElementsEquivalent(field, element)) {
              var kind = imported.first.equals(ref)
                         ? JavaErrorKinds.IMPORT_SINGLE_STATIC_FIELD_AMBIGUOUS
                         : JavaErrorKinds.IMPORT_SINGLE_STATIC_FIELD_ALREADY_DEFINED;
              myVisitor.report(kind.create(ref));
            }
            mySingleImportedFields.put(refName, Pair.create(ref, (PsiField)element));
          }
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

  void checkSingleImportClassConflict(@NotNull PsiImportStatement statement) {
    if (statement.isOnDemand()) return;
    PsiElement element = statement.resolve();
    if (element instanceof PsiClass psiClass) {
      String name = psiClass.getName();
      Pair<PsiImportStaticReferenceElement, PsiClass> imported = mySingleImportedClasses.get(name);
      PsiClass importedClass = Pair.getSecond(imported);
      if (importedClass != null && !myVisitor.file().getManager().areElementsEquivalent(importedClass, element)) {
        PsiJavaCodeReferenceElement reference = statement.getImportReference();
        if (reference != null) {
          myVisitor.report(JavaErrorKinds.IMPORT_SINGLE_CLASS_CONFLICT.create(reference, importedClass));
        }
        return;
      }
      mySingleImportedClasses.put(name, Pair.pair(null, psiClass));
    }
  }
}

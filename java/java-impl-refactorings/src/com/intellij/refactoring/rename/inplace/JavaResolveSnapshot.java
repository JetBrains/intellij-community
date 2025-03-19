// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.inplace;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.rename.RenameJavaMemberProcessor;
import com.intellij.refactoring.rename.ResolveSnapshotProvider;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

class JavaResolveSnapshot extends ResolveSnapshotProvider.ResolveSnapshot {
  private final Map<SmartPsiElementPointer<?>, SmartPsiElementPointer<?>> myReferencesMap = new HashMap<>();
  private final Project myProject;
  private final Document myDocument;

  JavaResolveSnapshot(final PsiElement scope) {
    myProject = scope.getProject();
    myDocument = FileDocumentManager.getInstance().getDocument(scope.getContainingFile().getViewProvider().getVirtualFile(), myProject);
    final SmartPointerManager pointerManager = SmartPointerManager.getInstance(myProject);
    final Map<PsiElement, SmartPsiElementPointer<?>> pointers = new HashMap<>();
    scope.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression refExpr) {
        if (!refExpr.isQualified()) {
          JavaResolveResult resolveResult = refExpr.advancedResolve(false);
          final PsiElement resolved = resolveResult.getElement();
          if ((resolved instanceof PsiField || resolved instanceof PsiClass) && resolveResult.isStaticsScopeCorrect()) {
            SmartPsiElementPointer<?> key = pointerManager.createSmartPsiElementPointer(refExpr);
            SmartPsiElementPointer<?> value = pointers.get(resolved);
            if (value == null) {
              value = pointerManager.createSmartPsiElementPointer(resolved);
              pointers.put(resolved, value);
            }
            myReferencesMap.put(key, value);
          }
        }
        super.visitReferenceExpression(refExpr);
      }
    });
  }

  @Override
  public void apply(String hidingLocalName) {
    PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
    for (Map.Entry<SmartPsiElementPointer<?>, SmartPsiElementPointer<?>> entry : myReferencesMap.entrySet()) {
      qualify(entry.getKey().getElement(), entry.getValue().getElement(), hidingLocalName);
    }
  }

  private static void qualify(PsiElement referent, PsiElement referee, String hidingLocalName) {
    if (referent instanceof PsiReferenceExpression ref &&
        referee instanceof PsiMember && !ref.isQualified() &&
        hidingLocalName.equals(ref.getReferenceName())) {
      final PsiElement newlyResolved = ref.resolve();
      if (referee.getManager().areElementsEquivalent(newlyResolved, referee)) return;
      RenameJavaMemberProcessor.qualifyMember((PsiMember)referee, referent, hidingLocalName);
    }
  }
}

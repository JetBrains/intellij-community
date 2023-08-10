// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

class PackageLocalsUsageCollector extends JavaRecursiveElementWalkingVisitor {
  private final HashMap<PsiElement,HashSet<PsiElement>> myReported = new HashMap<>();
  private final PsiElement[] myElementsToMove;
  private final MultiMap<PsiElement, @DialogMessage String> myConflicts;
  private final PackageWrapper myTargetPackage;

  PackageLocalsUsageCollector(PsiElement[] elementsToMove, PackageWrapper targetPackage, MultiMap<PsiElement, @DialogMessage String> conflicts) {
    myElementsToMove = elementsToMove;
    myConflicts = conflicts;
    myTargetPackage = targetPackage;
  }

  @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
    super.visitReferenceExpression(expression);
    visitReferenceElement(expression);
  }

  @Override public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
    super.visitReferenceElement(reference);
    PsiElement resolved = reference.resolve();
    visitResolvedReference(resolved, reference);
  }

  private void visitResolvedReference(PsiElement resolved, PsiJavaCodeReferenceElement reference) {
    if (resolved instanceof PsiModifierListOwner) {
      if (((PsiModifierListOwner)resolved).hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        PsiFile aFile = resolved.getContainingFile();
        if (aFile != null && !isInsideMoved(resolved)) {
          final PsiDirectory containingDirectory = aFile.getContainingDirectory();
          if (containingDirectory != null) {
            PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(containingDirectory);
            if (aPackage != null && !myTargetPackage.equalToPackage(aPackage)) {
              HashSet<PsiElement> reportedRefs = myReported.get(resolved);
              if (reportedRefs == null) {
                reportedRefs = new HashSet<>();
                myReported.put(resolved, reportedRefs);
              }
              PsiElement container = ConflictsUtil.getContainer(reference);
              if (!reportedRefs.contains(container)) {
                final String message = JavaRefactoringBundle.message("0.uses.a.package.local.1",
                                                                 RefactoringUIUtil.getDescription(container, true),
                                                                 RefactoringUIUtil.getDescription(resolved, true));
                myConflicts.putValue(resolved, StringUtil.capitalize(message));
                reportedRefs.add(container);
              }
            }
          }
        }
      }
    }
  }

  private boolean isInsideMoved(PsiElement place) {
    for (PsiElement element : myElementsToMove) {
      if (element.getContainingFile() != null) {
        if (PsiTreeUtil.isAncestor(element, place, false)) return true;
      }
    }
    return false;
  }
}
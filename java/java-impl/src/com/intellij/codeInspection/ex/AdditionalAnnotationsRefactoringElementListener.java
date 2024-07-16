// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.UndoRefactoringElementAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AdditionalAnnotationsRefactoringElementListener implements RefactoringElementListenerProvider {
  @Override
  public @Nullable RefactoringElementListener getListener(final PsiElement psiElement) {
    if (!(psiElement instanceof PsiClass)) return null;
    final String oldName = ((PsiClass)psiElement).getQualifiedName();
    if (oldName == null) return null;
    final EntryPointsManagerBase entryPointsManager = EntryPointsManagerBase.getInstance(psiElement.getProject());
    return new UndoRefactoringElementAdapter() {
      @Override
      protected void refactored(@NotNull PsiElement element, @Nullable String oldQualifiedName) {
        if (element instanceof PsiClass) {
          final String newQualifiedName = ((PsiClass)element).getQualifiedName();
          if (newQualifiedName != null) {
            final int idx = entryPointsManager.ADDITIONAL_ANNOTATIONS.indexOf(oldQualifiedName != null ? newQualifiedName : oldName);
            if (idx > -1) {
              entryPointsManager.ADDITIONAL_ANNOTATIONS.remove(idx);
              entryPointsManager.ADDITIONAL_ANNOTATIONS.add(idx, oldQualifiedName != null ? oldQualifiedName : newQualifiedName);
            }
          }
        }
      }
    };
  }
}

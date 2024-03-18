// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExternalAnnotationsRefactoringListenerProvider implements RefactoringElementListenerProvider {
  @Nullable
  @Override
  public RefactoringElementListener getListener(PsiElement element) {
    if (element instanceof PsiModifierListOwner modifierListOwner) {
      Project project = element.getProject();
      ExternalAnnotationsManager externalAnnotationsManager = ExternalAnnotationsManager.getInstance(project);
      PsiAnnotation[] annotations = externalAnnotationsManager.findExternalAnnotations(modifierListOwner);
      if (annotations != null) {
        String oldExternalName = PsiFormatUtil.getExternalName(modifierListOwner, false, Integer.MAX_VALUE);
        if (oldExternalName == null) {
          return null;
        }
        return new RefactoringElementListener() {
          private void elementRenamedOrMoved(@NotNull PsiElement newElement) {
            if (newElement instanceof PsiModifierListOwner owner) {
              externalAnnotationsManager.elementRenamedOrMoved(owner, oldExternalName);
            }
          }

          @Override
          public void elementMoved(@NotNull PsiElement newElement) {
            elementRenamedOrMoved(newElement);
          }

          @Override
          public void elementRenamed(@NotNull PsiElement newElement) {
            elementRenamedOrMoved(newElement);
          }
        };
      }
    }
    return null;
  }
}

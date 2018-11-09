// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class RemoveAnnotationQuickFix implements LocalQuickFix {
  private final SmartPsiElementPointer<PsiAnnotation> myAnnotation;
  private final SmartPsiElementPointer<PsiModifierListOwner> myListOwner;

  public RemoveAnnotationQuickFix(@NotNull PsiAnnotation annotation, @Nullable PsiModifierListOwner listOwner) {
    Project project = annotation.getProject();
    SmartPointerManager pm = SmartPointerManager.getInstance(project);
    myAnnotation = pm.createSmartPsiElementPointer(annotation);
    myListOwner = listOwner == null ? null : pm.createSmartPsiElementPointer(listOwner);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("remove.annotation");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiAnnotation annotation = myAnnotation.getElement();
    if (annotation == null) return;
    if (annotation.isPhysical()) {
      if (!FileModificationService.getInstance().preparePsiElementForWrite(annotation)) return;
      WriteAction.run(() -> annotation.delete());
    }
    else {
      PsiModifierListOwner listOwner = myListOwner.getElement();
      String qualifiedName = annotation.getQualifiedName();
      if (listOwner != null && qualifiedName != null) {
        ExternalAnnotationsManager.getInstance(project).deannotate(listOwner, qualifiedName);
      }
    }
  }
}
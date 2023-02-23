// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.NotNull;


public class AddAnnotationFixWithoutArgFix extends AddAnnotationFix {
  public AddAnnotationFixWithoutArgFix(@NotNull String fqn,
                                       @NotNull PsiModifierListOwner modifierListOwner,
                                       String @NotNull ... annotationsToRemove) {
    super(fqn, modifierListOwner, PsiNameValuePair.EMPTY_ARRAY, ExternalAnnotationsManager.AnnotationPlace.IN_CODE, annotationsToRemove);
  }

  public static boolean isApplicable(@NotNull PsiModifierListOwner modifierListOwner, @NotNull String annotationFQN) {
    VirtualFile file = modifierListOwner.getContainingFile().getVirtualFile();
    return ProjectFileIndex.getInstance(modifierListOwner.getProject()).isInSourceContent(file)
           && AddAnnotationPsiFix.isAvailable(modifierListOwner, annotationFQN);
  }

  @Override
  protected PsiAnnotation addAnnotation(PsiAnnotationOwner annotationOwner, String fqn) {
    if (annotationOwner instanceof PsiModifierList modifierList) {
      Project project = modifierList.getProject();
      PsiAnnotation annotation = PsiElementFactory.getInstance(project).createAnnotationFromText("@" + fqn + "()", modifierList);
      return (PsiAnnotation)modifierList.addBefore(annotation, modifierList.getFirstChild());
    }
    return null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    super.invoke(project, editor, file);
    if (myPairs.length == 0) {
      PsiModifierListOwner owner = (PsiModifierListOwner)getStartElement();
      PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, myAnnotation);
      if (annotation == null) return;
      if (EDT.isCurrentThreadEdt()) {
        editor.getCaretModel().moveToOffset(annotation.getParameterList().getTextOffset() + 1);
        EditorModificationUtilEx.scrollToCaret(editor);
      }
    }
  }
}

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 20, 2007
 * Time: 2:57:59 PM
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddNullableNotNullAnnotationFix extends AddAnnotationFix {
  public AddNullableNotNullAnnotationFix(final String annotation, final String annotationToRemove) {
    super(annotation, annotationToRemove);
  }

  public AddNullableNotNullAnnotationFix(final String fqn, final PsiModifierListOwner owner, final String annotationToRemove) {
    super(fqn, owner, annotationToRemove);
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, @Nullable final PsiElement element) {
     if (!super.isAvailable(project, editor, element)) {
      return false;
    }
    PsiModifierListOwner owner = getContainer(element);
    if (owner == null || AnnotationUtil.isAnnotated(owner, getAnnotationsToRemove()[0], false)) {
      return false;
    }
    if (owner instanceof PsiMethod) {
      PsiType returnType = ((PsiMethod)owner).getReturnType();

      return returnType != null && !(returnType instanceof PsiPrimitiveType);
    }
    return true;
  }
}
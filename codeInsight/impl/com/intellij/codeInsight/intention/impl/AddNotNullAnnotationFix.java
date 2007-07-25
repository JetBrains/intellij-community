/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 20, 2007
 * Time: 2:57:38 PM
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class AddNotNullAnnotationFix extends AddAnnotationFix {
  public AddNotNullAnnotationFix() {
    super(AnnotationUtil.NOT_NULL, AnnotationUtil.NULLABLE);
  }
  public AddNotNullAnnotationFix(PsiModifierListOwner owner) {
    super(AnnotationUtil.NOT_NULL, owner, AnnotationUtil.NULLABLE);
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    if (!super.isAvailable(project, editor, file)) {
      return false;
    }
    PsiModifierListOwner owner = getContainer(editor, file);
    if (owner == null || AnnotationUtil.isAnnotated(owner, AnnotationUtil.NULLABLE, false)) {
      return false;
    }
    PsiType returnType = ((PsiMethod)owner).getReturnType();

    return returnType != null && !(returnType instanceof PsiPrimitiveType);
  }
}
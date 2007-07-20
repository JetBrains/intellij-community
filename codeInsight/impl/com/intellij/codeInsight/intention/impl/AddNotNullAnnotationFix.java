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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;

public class AddNotNullAnnotationFix extends AddAnnotationFix {
  public AddNotNullAnnotationFix() {
    super(AnnotationUtil.NOT_NULL, AnnotationUtil.NULLABLE);
  }
  public AddNotNullAnnotationFix(PsiModifierListOwner owner) {
    super(AnnotationUtil.NOT_NULL, owner, AnnotationUtil.NULLABLE);
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return super.isAvailable(project, editor, file) &&
           !AnnotationUtil.isAnnotated(getContainer(editor, file), AnnotationUtil.NULLABLE, false);
  }
}
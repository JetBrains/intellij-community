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
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class AddNullableAnnotationFix extends AddAnnotationFix {
  public AddNullableAnnotationFix() {
    super(AnnotationUtil.NULLABLE, AnnotationUtil.NOT_NULL);
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return super.isAvailable(project, editor, file) &&
           !AnnotationUtil.isAnnotated(getContainer(editor, file), AnnotationUtil.NOT_NULL, false);
  }
}
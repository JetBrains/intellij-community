// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AnnotationMethodReturnTypeFix extends MethodReturnTypeFix {
  private final boolean myFromDefaultValue;

  public AnnotationMethodReturnTypeFix(@NotNull PsiMethod method, @NotNull PsiType returnType, boolean fromDefaultValue) {
    super(method, returnType, false);
    myFromDefaultValue = fromDefaultValue;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    super.invoke(project, psiFile, editor, startElement, endElement);
    if (!myFromDefaultValue && startElement instanceof PsiAnnotationMethod) {
      WriteCommandAction.writeCommandAction(project, psiFile).run(() -> {
        final PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)startElement).getDefaultValue();
        if (defaultValue != null) {
          if (editor != null) {
            editor.getCaretModel().moveToOffset(defaultValue.getTextOffset());
          }
          new CommentTracker().deleteAndRestoreComments(defaultValue);
        }
      });
    }
  }

  @Override
  protected void updateMethodType(@NotNull PsiMethod method, @NotNull PsiType type) {
    super.updateMethodType(method, type);
    if (!myFromDefaultValue && method instanceof PsiAnnotationMethod) {
      PsiAnnotationMemberValue value = ((PsiAnnotationMethod)method).getDefaultValue();
      if (value != null) {
        new CommentTracker().deleteAndRestoreComments(value);
      }
    }
  }
}

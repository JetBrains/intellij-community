// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class QualifyStaticMethodCallFix extends StaticImportMethodFix {
  public QualifyStaticMethodCallFix(@NotNull PsiFile file, @NotNull PsiMethodCallExpression methodCallExpression) {
    super(file, methodCallExpression);
  }

  @NotNull
  @Override
  protected String getBaseText() {
    return JavaBundle.message("qualify.static.call.fix.text");
  }

  @NotNull
  @Override
  protected QuestionAction createQuestionAction(@NotNull List<? extends PsiMethod> methodsToImport,
                                                @NotNull Project project,
                                                Editor editor) {
    return new StaticImportMemberQuestionAction<PsiMethod>(project, editor, methodsToImport, myReferencePointer) {
      @Override
      protected void doImport(@NotNull PsiMethod toImport) {
        PsiMethodCallExpression element = myReferencePointer.getElement();
        if (element == null) return;
        WriteCommandAction.runWriteCommandAction(project, JavaBundle.message("qualify.static.access.command.name"),
                                                 null, () -> qualifyStatically(toImport, project, element.getMethodExpression()));
      }
    };
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return generatePreview(file, (expression, method) -> qualifyStatically(method, project, ((PsiMethodCallExpression)expression).getMethodExpression()));
  }

  @Override
  boolean toAddStaticImports() {
    return false;
  }

  static void qualifyStatically(@NotNull PsiMember toImport,
                                @NotNull Project project,
                                @NotNull PsiReferenceExpression qualifiedExpression) {
    PsiClass containingClass = toImport.getContainingClass();
    if (containingClass == null) return;
    PsiReferenceExpression qualifier = JavaPsiFacade.getElementFactory(project).createReferenceExpression(containingClass);
    qualifiedExpression.setQualifierExpression(qualifier);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(qualifiedExpression);
  }
}

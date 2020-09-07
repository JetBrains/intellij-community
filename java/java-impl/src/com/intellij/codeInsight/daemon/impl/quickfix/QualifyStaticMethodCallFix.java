/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

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
  protected StaticImportMethodQuestionAction<PsiMethod> createQuestionAction(@NotNull List<? extends PsiMethod> methodsToImport,
                                                                             @NotNull Project project,
                                                                             Editor editor) {
    return new StaticImportMethodQuestionAction<>(project, editor, methodsToImport, myRef) {
      @Override
      protected void doImport(PsiMethod toImport) {
        PsiMethodCallExpression element = myRef.getElement();
        if (element == null) return;
        qualifyStatically(toImport, project, element.getMethodExpression());
      }
    };
  }

  @Override
  protected boolean toAddStaticImports() {
    return false;
  }

  static void qualifyStatically(@NotNull PsiMember toImport,
                                @NotNull Project project,
                                @NotNull PsiReferenceExpression qualifiedExpression) {
    PsiClass containingClass = toImport.getContainingClass();
    if (containingClass == null) return;
    PsiReferenceExpression qualifier = JavaPsiFacade.getElementFactory(project).createReferenceExpression(containingClass);
    WriteCommandAction.runWriteCommandAction(project, JavaBundle.message("qualify.static.access.command.name"), null, () -> {
                                               qualifiedExpression.setQualifierExpression(qualifier);
                                               JavaCodeStyleManager.getInstance(project).shortenClassReferences(qualifiedExpression);
                                             }
    );
  }
}

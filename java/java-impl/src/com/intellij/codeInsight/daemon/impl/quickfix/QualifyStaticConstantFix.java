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

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class QualifyStaticConstantFix extends StaticImportConstantFix {
  public QualifyStaticConstantFix(@NotNull PsiJavaCodeReferenceElement referenceElement) {
    super(referenceElement);
  }

  @NotNull
  @Override
  protected String getBaseText() {
    return "Qualify static constant access";
  }

  @NotNull
  @Override
  protected StaticImportMethodQuestionAction<PsiField> createQuestionAction(List<PsiField> fieldsToImport,
                                                                            @NotNull Project project,
                                                                            Editor editor) {
    return new StaticImportMethodQuestionAction<PsiField>(project, editor, fieldsToImport, myRef) {
      @NotNull
      @Override
      protected String getPopupTitle() {
        return QuickFixBundle.message("field.to.import.chooser.title");
      }

      @Override
      protected void doImport(PsiField toImport) {
        PsiJavaCodeReferenceElement element = myRef.getElement();
        if (!(element instanceof PsiReferenceExpression)) return;
        QualifyStaticMethodCallFix.qualifyStatically(toImport, project, (PsiReferenceExpression)element);
      }
    };
  }

  @Override
  protected boolean toAddStaticImports() {
    return false;
  }
}

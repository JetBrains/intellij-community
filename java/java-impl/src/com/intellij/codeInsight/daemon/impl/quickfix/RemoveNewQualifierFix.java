/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 29, 2002
 * Time: 3:02:17 PM
 * To change this template use Options | File Templates.
 */
public class RemoveNewQualifierFix implements IntentionAction {
  private final PsiNewExpression expression;
  private final PsiClass aClass;

  public RemoveNewQualifierFix(@NotNull PsiNewExpression expression, PsiClass aClass) {
    this.expression = expression;
    this.aClass = aClass;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("remove.qualifier.fix");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.qualifier.fix");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return
      expression.isValid() && (aClass == null || aClass.isValid()) && expression.getManager().isInProject(expression);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(expression.getContainingFile())) return;
    PsiJavaCodeReferenceElement classReference = expression.getClassReference();
    expression.getQualifier().delete();
    if (aClass != null && classReference != null) {
      classReference.bindToElement(aClass);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

}

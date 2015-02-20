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
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class GeneralizeCatchFix implements IntentionAction {
  private final PsiElement myElement;
  private final PsiClassType myUnhandledException;
  private PsiTryStatement myTryStatement;
  private PsiParameter myCatchParameter;

  public GeneralizeCatchFix(@NotNull PsiElement element, @NotNull PsiClassType unhandledException) {
    myElement = element;
    myUnhandledException = unhandledException;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("generalize.catch.text",
                                  JavaHighlightUtil.formatType(myCatchParameter == null ? null : myCatchParameter.getType()),
                                  JavaHighlightUtil.formatType(myUnhandledException));
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("generalize.catch.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(myElement.isValid()
          && myUnhandledException.isValid()
          && myElement.getManager().isInProject(myElement))) return false;
    // find enclosing try
    PsiElement element = myElement;
    while (element != null) {
      if (PsiUtil.isTryBlock(element) || element instanceof PsiResourceList) {
        myTryStatement = (PsiTryStatement)element.getParent();
        break;
      }
      if (element instanceof PsiMethod || element instanceof PsiFunctionalExpression || (element instanceof PsiClass && !(element instanceof PsiAnonymousClass))) break;
      element = element.getParent();
    }
    if (myTryStatement == null) return false;
    // check we can generalize at least one catch
    PsiParameter[] catchBlockParameters = myTryStatement.getCatchBlockParameters();
    for (PsiParameter catchBlockParameter : catchBlockParameters) {
      PsiType type = catchBlockParameter.getType();
      if (myUnhandledException.isAssignableFrom(type)) {
        myCatchParameter = catchBlockParameter;
        break;
      }
    }
    return myCatchParameter != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().prepareFileForWrite(myElement.getContainingFile())) return;
    PsiElementFactory factory = JavaPsiFacade.getInstance(myElement.getProject()).getElementFactory();
    PsiTypeElement type = factory.createTypeElement(myUnhandledException);
    myCatchParameter.getTypeElement().replace(type);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}

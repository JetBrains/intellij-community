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
package com.intellij.refactoring.introduceVariable;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.chainCall.ChainCallExtractor;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Tagir Valeev
 */
public class ChainCallInplaceIntroducer extends JavaVariableInplaceIntroducer {
  private PsiParameter myParameter;
  private PsiMethodCallExpression myCall;

  public ChainCallInplaceIntroducer(Project project,
                                    IntroduceVariableSettings settings,
                                    PsiElement chosenAnchor,
                                    Editor editor,
                                    PsiExpression expr,
                                    PsiExpression[] occurrences,
                                    TypeSelectorManagerImpl selectorManager,
                                    String title) {
    super(project, settings, chosenAnchor, editor, expr, true, occurrences, selectorManager, title);
  }

  @Nullable
  @Override
  protected PsiVariable getVariable() {
    if (myParameter != null && myParameter.isValid()) {
      return myParameter;
    }
    return super.getVariable();
  }

  @Nullable
  @Override
  protected PsiElement checkLocalScope() {
    return myCall;
  }

  @Nullable
  @Override
  protected PsiVariable introduceVariable() {
    PsiVariable variable = super.introduceVariable();
    if (variable instanceof PsiLocalVariable) {
      PsiLambdaExpression lambda = ApplicationManager.getApplication().runWriteAction(
        (Computable<PsiLambdaExpression>)() -> ChainCallExtractor.extractMappingStep(myProject, (PsiLocalVariable)variable));
      if (lambda != null) {
        PsiParameter parameter = ArrayUtil.getFirstElement(lambda.getParameterList().getParameters());
        myParameter = parameter;
        myCall = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
        myExprMarker = null;
        myExpr = null;
        setAdvertisementText(null);
        PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(myEditor.getDocument());
        return parameter;
      }
      else if (!variable.isValid()) {
        return null;
      }
    }
    return variable;
  }
}

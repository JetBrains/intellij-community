/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.intellij.refactoring.introduceVariable.InputValidator;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.refactoring.introduceVariable.IntroduceVariableSettings;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NON_VOID;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

// todo: support for int[].var (parses as .class access!)
public class IntroduceVariablePostfixTemplate extends PostfixTemplateWithExpressionSelector {
  public IntroduceVariablePostfixTemplate() {
    super("var", "T name = expr", selectorAllExpressionsWithCurrentOffset(IS_NON_VOID));
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    // for advanced stuff use ((PsiJavaCodeReferenceElement)expression).advancedResolve(true).getElement();
    IntroduceVariableHandler handler =
      ApplicationManager.getApplication().isUnitTestMode() ? getMockHandler() : new IntroduceVariableHandler();
    handler.invoke(expression.getProject(), editor, (PsiExpression)expression);
  }

  @NotNull
  private static IntroduceVariableHandler getMockHandler() {
    return new IntroduceVariableHandler() {
      // mock default settings
      @Override
      public final IntroduceVariableSettings getSettings(Project project, Editor editor, final PsiExpression expr,
                                                         PsiExpression[] occurrences, TypeSelectorManagerImpl typeSelectorManager,
                                                         boolean declareFinalIfAll, boolean anyAssignmentLHS, InputValidator validator,
                                                         PsiElement anchor, OccurrencesChooser.ReplaceChoice replaceChoice) {
        return new IntroduceVariableSettings() {
          @Override
          public String getEnteredName() {
            return "foo";
          }

          @Override
          public boolean isReplaceAllOccurrences() {
            return false;
          }

          @Override
          public boolean isDeclareFinal() {
            return false;
          }

          @Override
          public boolean isReplaceLValues() {
            return false;
          }

          @Override
          public PsiType getSelectedType() {
            return expr.getType();
          }

          @Override
          public boolean isOK() {
            return true;
          }
        };
      }
    };
  }
}
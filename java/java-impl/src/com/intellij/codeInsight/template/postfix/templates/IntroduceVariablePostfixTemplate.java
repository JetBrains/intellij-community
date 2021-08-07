// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
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

  @Override
  public boolean isApplicable(@NotNull PsiElement context,
                              @NotNull Document copyDocument, int newOffset) {
    // Non-inplace mode would require a modal dialog, which is not allowed under postfix templates 
    return EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled() &&
           super.isApplicable(context, copyDocument, newOffset);
  }

  @NotNull
  private static IntroduceVariableHandler getMockHandler() {
    return new IntroduceVariableHandler() {
      // mock default settings
      @Override
      public IntroduceVariableSettings getSettings(Project project, Editor editor, final PsiExpression expr,
                                                   PsiExpression[] occurrences, TypeSelectorManagerImpl typeSelectorManager,
                                                   boolean declareFinalIfAll, boolean anyAssignmentLHS, InputValidator validator,
                                                   PsiElement anchor, JavaReplaceChoice replaceChoice) {
        return new IntroduceVariableSettings() {
          @Override
          public @NlsSafe String getEnteredName() {
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

      @Override
      protected boolean isInplaceAvailableInTestMode() {
        return true;
      }
    };
  }

  @Override
  protected void prepareAndExpandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    //no write action
    expandForChooseExpression(expression, editor);
  }
}
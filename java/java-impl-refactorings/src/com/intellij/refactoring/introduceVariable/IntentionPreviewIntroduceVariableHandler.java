// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceVariable;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.refactoring.introduceField.ElementToWorkOn;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;

public class IntentionPreviewIntroduceVariableHandler extends IntroduceVariableHandler {
  public static final Key<Boolean> INTENTION_PREVIEW_INTRODUCER = Key.create("INTENTION_PREVIEW_INTRODUCER");

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    assert elements.length == 1 && elements[0] instanceof PsiExpression;
    PsiExpression expression = (PsiExpression)elements[0];
    expression.putUserData(ElementToWorkOn.REPLACE_NON_PHYSICAL, true);
    expression.putUserData(INTENTION_PREVIEW_INTRODUCER, true);
    PsiType type = CommonJavaRefactoringUtil.getTypeByExpressionWithExpectedType(expression);
    final SuggestedNameInfo suggestedName = CommonJavaRefactoringUtil.getSuggestedName(type, expression, expression);
    final String variableName = suggestedName.names.length > 0 ? suggestedName.names[0] : "v";
    VariableExtractor extractor =
      new VariableExtractor(project, expression, null, expression, PsiExpression.EMPTY_ARRAY, new IntroduceVariableSettings() {
        @Override

        public @NlsSafe String getEnteredName() {
          return variableName;
        }
        @Override
        public boolean isReplaceAllOccurrences() {
          return false;
        }

        @Override
        public boolean isDeclareFinal() {
          return IntroduceVariableBase.createFinals(expression.getContainingFile());
        }

        @Override
        public boolean isDeclareVarType() {
          return IntroduceVariableBase.createVarType() ;
        }

        @Override
        public boolean isReplaceLValues() {
          return false;
        }

        @Override
        public PsiType getSelectedType() {
          return type;
        }

        @Override
        public boolean isOK() {
          return true;
        }
      });
    extractor.extractVariable();
  }
}

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

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_BOOLEAN;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.JAVA_PSI_INFO;

public class WhileStatementPostfixTemplate extends TypedPostfixTemplate {
  public WhileStatementPostfixTemplate() {
    super("while", "while (expr)", JAVA_PSI_INFO, IS_BOOLEAN);
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiElement expression = myPsiInfo.getTopmostExpression(context);
    assert expression != null;

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    PsiWhileStatement whileStatement = (PsiWhileStatement)factory.createStatementFromText("while(expr)", context);
    PsiExpression condition = whileStatement.getCondition();
    assert condition != null;
    condition.replace(expression);
    PsiElement replacedWhileStatement = expression.replace(whileStatement);
    if (replacedWhileStatement instanceof PsiWhileStatement) {
      PsiJavaToken parenth = ((PsiWhileStatement)replacedWhileStatement).getRParenth();
      if (parenth != null) {
        editor.getCaretModel().moveToOffset(parenth.getTextRange().getEndOffset());
      }
    }
  }
}


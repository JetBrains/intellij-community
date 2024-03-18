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

import com.intellij.codeInsight.generation.surroundWith.JavaWithIfExpressionSurrounder;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.*;

public class IfStatementPostfixTemplate extends IfPostfixTemplateBase implements DumbAware {
  public IfStatementPostfixTemplate() {
    super(JAVA_PSI_INFO, selectorTopmost(IS_BOOLEAN));
  }

  @Override
  protected PsiElement getWrappedExpression(PsiElement expression) {
    return CommonJavaRefactoringUtil.unparenthesizeExpression((PsiExpression)expression);
  }

  @NotNull
  @Override
  protected Surrounder getSurrounder() {
    return new JavaWithIfExpressionSurrounder();
  }
}


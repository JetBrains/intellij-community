/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Danila Ponomarenko
 */
public class JavaBinaryExpressionUnwrapper extends JavaUnwrapper {
  public JavaBinaryExpressionUnwrapper() {
    super("");
  }

  @Override
  public String getDescription(PsiElement e) {
    return CodeInsightBundle.message("unwrap.with.placeholder", e.getText());
  }

  @Override
  public boolean isApplicableTo(PsiElement e) {
    return e.getParent() instanceof PsiBinaryExpression;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    final PsiBinaryExpression parent = (PsiBinaryExpression)element.getParent();

    final PsiExpression lOperand = parent.getLOperand();
    final PsiExpression rOperand = parent.getROperand();

    if (rOperand == null) {
      return;
    }

    if (Comparing.equal(lOperand, element)) {
      context.extractElement(rOperand, parent);
    }
    else {
      context.extractElement(lOperand, parent);
    }
    context.delete(parent);
  }
}

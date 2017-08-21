/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaLambdaUnwrapper extends JavaUnwrapper {
  public JavaLambdaUnwrapper() {
    super(CodeInsightBundle.message("unwrap.lambda"));
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    return e instanceof PsiLambdaExpression;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiElement from = JavaAnonymousUnwrapper.findElementToExtractFrom(element);
    PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)element;
    PsiElement body = lambdaExpression.getBody();
    if (body instanceof PsiExpression || body instanceof PsiCodeBlock && ((PsiCodeBlock)body).getStatements().length == 1) {
      List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(lambdaExpression);
      if (returnExpressions.size() == 1
          && !PsiType.VOID.equals(returnExpressions.get(0).getType())
          && JavaAnonymousUnwrapper.toAssignment(context, from, returnExpressions.get(0))) {
        return;
      }
    }

    if (body instanceof PsiCodeBlock) {
      context.extractFromCodeBlock((PsiCodeBlock)body, from);
    }
    else {
      context.extractElement(body, from);
      if (context.myIsEffective && !(from.getParent() instanceof PsiLambdaExpression)) {
        PsiStatement emptyStatement = JavaPsiFacade.getElementFactory(from.getProject()).createStatementFromText(";", from);
        from.getParent().addBefore(emptyStatement, from);
      }
    }
    context.deleteExactly(from);
  }
}

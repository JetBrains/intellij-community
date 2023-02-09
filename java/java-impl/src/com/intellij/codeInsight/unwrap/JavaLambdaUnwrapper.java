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

import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaLambdaUnwrapper extends JavaUnwrapper {
  public JavaLambdaUnwrapper() {
    super(JavaBundle.message("unwrap.lambda"));
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    return e instanceof PsiLambdaExpression;
  }

  @Override
  public PsiElement collectAffectedElements(@NotNull PsiElement e, @NotNull List<? super PsiElement> toExtract) {
     super.collectAffectedElements(e, toExtract);
     return JavaAnonymousUnwrapper.findElementToExtractFrom(e);
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiElement from = JavaAnonymousUnwrapper.findElementToExtractFrom(element);
    PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)element;
    PsiElement body = lambdaExpression.getBody();
    if (body instanceof PsiExpression || body instanceof PsiCodeBlock && ((PsiCodeBlock)body).getStatementCount() == 1) {
      List<PsiExpression> returnExpressions = LambdaUtil.getReturnExpressions(lambdaExpression);
      if (returnExpressions.size() == 1
          && !PsiTypes.voidType().equals(returnExpressions.get(0).getType())
          && JavaAnonymousUnwrapper.toAssignment(context, from, returnExpressions.get(0))) {
        return;
      }
    }

    if (body instanceof PsiCodeBlock) {
      if (from.getParent() instanceof PsiLambdaExpression) {
        context.extractElement(body, from);
      }
      else {
        context.extractFromCodeBlock((PsiCodeBlock)body, from);
      }
    }
    else {
      context.extractElement(body, from);
      if (context.isEffective() && !(from.getParent() instanceof PsiLambdaExpression)) {
        PsiStatement emptyStatement = JavaPsiFacade.getElementFactory(from.getProject()).createStatementFromText(";", from);
        from.getParent().addBefore(emptyStatement, from);
      }
    }
    context.deleteExactly(from);
  }
}

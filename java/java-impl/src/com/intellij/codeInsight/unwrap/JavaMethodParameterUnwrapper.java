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
package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaMethodParameterUnwrapper extends JavaUnwrapper {
  private static final Logger LOG = Logger.getInstance(JavaMethodParameterUnwrapper.class);

  public JavaMethodParameterUnwrapper() {
    super("");
  }

  private static PsiElement adjustElementToTheLeft(PsiElement element) {
    if (PsiUtil.isJavaToken(element, JavaTokenType.RPARENTH)) {
      PsiElement prevSibling = element.getPrevSibling();
      if (prevSibling != null) {
        return prevSibling;
      }
    }
    return element;
  }

  @NotNull
  @Override
  public String getDescription(@NotNull PsiElement e) {
    String text = adjustElementToTheLeft(e).getText();
    if (text.length() > 20) text = text.substring(0, 17) + "...";
    return CodeInsightBundle.message("unwrap.with.placeholder", text);
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    e = adjustElementToTheLeft(e);
    final PsiElement parent = e.getParent();
    if (e instanceof PsiExpression){
      if (parent instanceof PsiExpressionList) {
        return true;
      }
      if (e instanceof PsiReferenceExpression && parent instanceof PsiCallExpression) {
        final PsiExpressionList argumentList = ((PsiCall)parent).getArgumentList();
        if (argumentList != null && argumentList.getExpressions().length == 1) {
          return true;
        }
      }
    } else if (e instanceof PsiJavaCodeReferenceElement) {
      if (parent instanceof PsiCall) {
        final PsiExpressionList argumentList = ((PsiCall)parent).getArgumentList();
        if (argumentList != null && argumentList.getExpressions().length == 1) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public PsiElement collectAffectedElements(@NotNull PsiElement e, @NotNull List<PsiElement> toExtract) {
    e = adjustElementToTheLeft(e);
    super.collectAffectedElements(e, toExtract);
    return isTopLevelCall(e) ? e.getParent() : e.getParent().getParent();
  }

  private static boolean isTopLevelCall(PsiElement e) {
    if (e instanceof PsiReferenceExpression && e.getParent() instanceof PsiCallExpression) return true;
    return e instanceof PsiJavaCodeReferenceElement && !(e instanceof PsiExpression);
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    element = adjustElementToTheLeft(element);
    PsiElement parent = element.getParent();
    if (parent == null) return;
    PsiElement methodCall = isTopLevelCall(element) ? parent : parent.getParent();
    final PsiElement extractedElement = isTopLevelCall(element) ? getArg(element) : element;
    context.extractElement(extractedElement, methodCall);
    if (methodCall.getParent() instanceof PsiExpressionList) {
      context.delete(methodCall);
    }
    else {
      context.deleteExactly(methodCall);
    }
  }

  private static PsiExpression getArg(PsiElement element) {
    final PsiExpressionList argumentList = ((PsiCall)element.getParent()).getArgumentList();
    LOG.assertTrue(argumentList != null);
    final PsiExpression[] args = argumentList.getExpressions();
    LOG.assertTrue(args.length == 1);
    return args[0];
  }
}

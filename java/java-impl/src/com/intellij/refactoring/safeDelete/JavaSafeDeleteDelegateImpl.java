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
package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceJavaDeleteUsageInfo;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class JavaSafeDeleteDelegateImpl implements JavaSafeDeleteDelegate {
  @Override
  public void createUsageInfoForParameter(final PsiReference reference,
                                          final List<UsageInfo> usages,
                                          final PsiParameter parameter,
                                          final PsiMethod method) {
    int index = method.getParameterList().getParameterIndex(parameter);
    final PsiElement element = reference.getElement();
    PsiCall call = null;
    if (element instanceof PsiCall) {
      call = (PsiCall)element;
    }
    else {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiCall) {
        call = (PsiCall)parent;
      } else if (parent instanceof PsiAnonymousClass) {
        call = (PsiNewExpression)parent.getParent();
      }
    }
    if (call != null) {
      final PsiExpressionList argList = call.getArgumentList();
      if (argList != null) {
        final PsiExpression[] args = argList.getExpressions();
        if (index < args.length) {
          if (!parameter.isVarArgs()) {
            usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(args[index], parameter, true));
          }
          else {
            for (int i = index; i < args.length; i++) {
              usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(args[i], parameter, true));
            }
          }
        }
      }
    }
    else if (element instanceof PsiDocMethodOrFieldRef) {
      if (((PsiDocMethodOrFieldRef)element).getSignature() != null) {
        @NonNls final StringBuffer newText = new StringBuffer();
        newText.append("/** @see #").append(method.getName()).append('(');
        final List<PsiParameter> parameters = new ArrayList<>(Arrays.asList(method.getParameterList().getParameters()));
        parameters.remove(parameter);
        newText.append(StringUtil.join(parameters, psiParameter -> psiParameter.getType().getCanonicalText(), ","));
        newText.append(")*/");
        usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, parameter, true) {
          public void deleteElement() throws IncorrectOperationException {
            final PsiDocMethodOrFieldRef.MyReference javadocMethodReference =
              (PsiDocMethodOrFieldRef.MyReference)element.getReference();
            if (javadocMethodReference != null) {
              javadocMethodReference.bindToText(method.getContainingClass(), newText);
            }
          }
        });
      }
    }
    else if (element instanceof PsiMethodReferenceExpression) {
      usages.add(new SafeDeleteReferenceJavaDeleteUsageInfo(element, parameter, true) {
        public void deleteElement() throws IncorrectOperationException {
          final PsiExpression callExpression = LambdaRefactoringUtil.convertToMethodCallInLambdaBody((PsiMethodReferenceExpression)element);
          if (callExpression instanceof PsiCallExpression) {
            final PsiExpressionList expressionList = ((PsiCallExpression)callExpression).getArgumentList();
            if (expressionList != null) {
              final PsiExpression[] args = expressionList.getExpressions();
              if (index < args.length) {
                args[index].delete();
              }
            }
          }
        }
      });
    }
  }
}

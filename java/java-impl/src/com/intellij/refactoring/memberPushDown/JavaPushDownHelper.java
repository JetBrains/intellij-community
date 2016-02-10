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
package com.intellij.refactoring.memberPushDown;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaPushDownHelper extends PushDownHelper {
  @Override
  public void findUsages(@NotNull PushDownContext context, @NotNull List<UsageInfo> result) {
    PsiClass sourceClass = context.getSourceClass();
    final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(sourceClass);
    if (interfaceMethod != null && context.getMembersToMove().contains(interfaceMethod)) {
      FunctionalExpressionSearch.search(sourceClass).forEach(new Processor<PsiFunctionalExpression>() {
        @Override
        public boolean process(PsiFunctionalExpression expression) {
          result.add(new UsageInfo(expression));
          return true;
        }
      });
    }
  }

  @Override
  public void findConflicts(@NotNull PushDownContext context,
                            @NotNull List<? extends UsageInfo> usages,
                            @NotNull MultiMap<PsiElement, String> result) {
    for (UsageInfo info : usages) {
      final PsiElement element = info.getElement();
      if (element instanceof PsiFunctionalExpression) {
        result.putValue(element, RefactoringBundle.message("functional.interface.broken"));
      }
    }

    PsiClass sourceClass = context.getSourceClass();
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(sourceClass, CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE);
    if (annotation != null && context.getMembersToMove().contains(LambdaUtil.getFunctionalInterfaceMethod(sourceClass))) {
      result.putValue(annotation, RefactoringBundle.message("functional.interface.broken"));
    }
  }
}

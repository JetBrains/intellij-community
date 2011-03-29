/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PreferLocalVariablesLiteralsAndAnnoMethodsWeigher extends LookupElementWeigher {
  private final CompletionType myCompletionType;

  public PreferLocalVariablesLiteralsAndAnnoMethodsWeigher(CompletionType completionType) {
    super("local");
    myCompletionType = completionType;
  }

  enum MyResult {
    annoMethod,
    probableKeyword,
    localOrParameter,
    superMethodParameters,
    normal,
    classLiteral,
    className,
  }

  @NotNull
  @Override
  public MyResult weigh(@NotNull LookupElement item) {
    final Object object = item.getObject();

    if (object instanceof PsiKeyword) {
      String keyword = ((PsiKeyword)object).getText();
      if (PsiKeyword.RETURN.equals(keyword) || PsiKeyword.ELSE.equals(keyword) || PsiKeyword.FINALLY.equals(keyword)) {
        return MyResult.probableKeyword;
      }
    }

    if (object instanceof PsiLocalVariable || object instanceof PsiParameter || object instanceof PsiThisExpression) {
      return MyResult.localOrParameter;
    }

    if (myCompletionType == CompletionType.SMART) {
      if (object instanceof String && item.getUserData(JavaCompletionUtil.SUPER_METHOD_PARAMETERS) == Boolean.TRUE) {
        return MyResult.superMethodParameters;
      }
      return MyResult.normal;
    }

    if (myCompletionType == CompletionType.BASIC) {
      if (object instanceof PsiKeyword && PsiKeyword.CLASS.equals(item.getLookupString())) {
        return MyResult.classLiteral;
      }

      if (object instanceof PsiAnnotationMethod && ((PsiAnnotationMethod)object).getContainingClass().isAnnotationType()) {
        return MyResult.annoMethod;
      }

      if (object instanceof PsiClass) {
        return MyResult.className;
      }
    }

    return MyResult.normal;
  }
}

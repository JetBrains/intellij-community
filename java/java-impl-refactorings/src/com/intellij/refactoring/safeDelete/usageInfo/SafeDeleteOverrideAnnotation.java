/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;

public class SafeDeleteOverrideAnnotation extends SafeDeleteUsageInfo implements SafeDeleteCustomUsageInfo {
  public SafeDeleteOverrideAnnotation(PsiElement element, PsiElement referencedElement) {
    super(element, referencedElement);
  }

  public PsiMethod getMethod() {
    return (PsiMethod)getElement();
  }

  @Override
  public void performRefactoring() throws IncorrectOperationException {
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(getMethod(), true, Override.class.getName());
    if (annotation != null) {
      annotation.delete();
    }
  }
}

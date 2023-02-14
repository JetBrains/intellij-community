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

package com.intellij.refactoring.extractclass.usageInfo;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class BindJavadocReference extends FixableUsageInfo {
  private final String myQualifiedName;
  private final String myFieldName;

  public BindJavadocReference(final PsiElement element, final String qualifiedName, final String fieldName) {
    super(element);
    myQualifiedName = qualifiedName;
    myFieldName = fieldName;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    final PsiElement element = getElement();
    if (element != null && element.isValid()) {
      final Project project = element.getProject();
      final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(myQualifiedName, GlobalSearchScope.projectScope(project));
      if (psiClass != null) {
        final PsiField field = psiClass.findFieldByName(myFieldName, false);
        if (field != null) {
          final PsiReference reference = element.getReference();
          if (reference != null) {
            reference.bindToElement(field);
          }
        }
      }
    }
  }
}

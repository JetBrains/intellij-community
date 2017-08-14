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
package com.intellij.refactoring.changeSignature.inCallers;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.MemberNodeBase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JavaMethodNode extends JavaMemberNode<PsiMethod> {
  protected JavaMethodNode(PsiMethod method,
                           Set<PsiMethod> called,
                           Project project,
                           Runnable cancelCallback) {
    super(method, called, project, cancelCallback);
  }

  @Override
  protected MemberNodeBase<PsiMethod> createNode(PsiMethod caller, HashSet<PsiMethod> called) {
    return new JavaMethodNode(caller, called, myProject, myCancelCallback);
  }

  @Override
  protected List<PsiMethod> computeCallers() {
    final PsiReference[] refs = MethodReferencesSearch.search(myMethod).toArray(PsiReference.EMPTY_ARRAY);

    List<PsiMethod> result = new ArrayList<>();
    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      if (!(element instanceof PsiReferenceExpression) ||
          !(((PsiReferenceExpression)element).getQualifierExpression() instanceof PsiSuperExpression)) {
        final PsiElement enclosingContext = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiClass.class);
        if (enclosingContext instanceof PsiMethod && !result.contains(enclosingContext) &&
            !getMember().equals(enclosingContext) && !myCalled.contains(getMember())) { //do not add recursive methods
          result.add((PsiMethod)enclosingContext);
        }
        else if (element instanceof PsiClass) {
          final PsiClass aClass = (PsiClass)element;
          final PsiMethod method = JavaPsiFacade.getElementFactory(myProject).createMethodFromText(aClass.getName() + "(){}", aClass);
          if (!result.contains(method)) {
            result.add(method);
          }
        }
      }
    }
    return result;
  }
}

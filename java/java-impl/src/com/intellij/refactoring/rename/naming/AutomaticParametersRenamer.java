/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package com.intellij.refactoring.rename.naming;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.searches.OverridingMethodsSearch;

public class AutomaticParametersRenamer extends AutomaticRenamer {
  public AutomaticParametersRenamer(PsiParameter param, String newParamName) {
    final PsiElement scope = param.getDeclarationScope();
    if (scope instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)scope;
      final int parameterIndex = method.getParameterList().getParameterIndex(param);
      if (parameterIndex < 0) return;
      for (PsiMethod overrider : OverridingMethodsSearch.search(method)) {
        final PsiParameter[] parameters = overrider.getParameterList().getParameters();
        if (parameterIndex >= parameters.length) continue;
        final PsiParameter inheritedParam = parameters[parameterIndex];
        if (!Comparing.strEqual(inheritedParam.getName(), newParamName)) {
          myElements.add(inheritedParam);
          suggestAllNames(inheritedParam.getName(), newParamName);
        }
      }
    }
  }

  @Override
  public String getDialogTitle() {
    return JavaRefactoringBundle.message("rename.parameters.dialog.title");
  }

  @Override
  public String getDialogDescription() {
    return JavaRefactoringBundle.message("rename.parameter.in.hierarchy.to.dialog.description");
  }

  @Override
  public String entityName() {
    return JavaRefactoringBundle.message("automatic.parameter.renamer.entity.name");
  }

  @Override
  public boolean isSelectedByDefault() {
    return true;
  }
}
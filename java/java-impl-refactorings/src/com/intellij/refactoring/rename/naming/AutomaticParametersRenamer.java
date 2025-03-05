// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
    if (scope instanceof PsiMethod method) {
      final int parameterIndex = method.getParameterList().getParameterIndex(param);
      if (parameterIndex < 0) return;
      for (PsiMethod overrider : OverridingMethodsSearch.search(method).asIterable()) {
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
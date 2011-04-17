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
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class AnnotateOverriddenMethodParameterFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.AnnotateMethodFix");
  private final String myAnnotation;
  private final String[] myAnnosToRemove;

  public AnnotateOverriddenMethodParameterFix(final String fqn, String... annosToRemove) {
    myAnnotation = fqn;
    myAnnosToRemove = annosToRemove;
  }

  @NotNull
  public String getName() {
    return InspectionsBundle.message("annotate.overridden.methods.parameters", ClassUtil.extractClassName(myAnnotation));
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();

    PsiParameter parameter = PsiTreeUtil.getParentOfType(psiElement, PsiParameter.class, false);
    if (parameter == null) return;
    PsiMethod method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class);
    if (method == null) return;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    int index = ArrayUtil.find(parameters, parameter);

    List<PsiParameter> toAnnotate = new ArrayList<PsiParameter>();

    PsiMethod[] methods = OverridingMethodsSearch.search(method, GlobalSearchScope.allScope(project), true).toArray(PsiMethod.EMPTY_ARRAY);
    for (PsiMethod psiMethod : methods) {
      PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
      if (index >= psiParameters.length) continue;
      PsiParameter psiParameter = psiParameters[index];
      if (!AnnotationUtil.isAnnotated(psiParameter, myAnnotation, false) && psiMethod.getManager().isInProject(psiMethod)) {
        toAnnotate.add(psiParameter);
      }
    }

    CodeInsightUtilBase.preparePsiElementsForWrite(toAnnotate);
    for (PsiParameter psiParam : toAnnotate) {
      try {
        assert psiParam != null : toAnnotate;
        new AddAnnotationFix(myAnnotation, psiParam, myAnnosToRemove).invoke(project, null, psiParam.getContainingFile());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }
}

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
package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
class AnnotateOverriddenMethodParameterFix implements LocalQuickFix {
  private final String myAnnotation;
  private final String[] myAnnosToRemove;

  AnnotateOverriddenMethodParameterFix(@NotNull String annotationFQN, @NotNull String... annosToRemove) {
    myAnnotation = annotationFQN;
    myAnnosToRemove = annosToRemove;
  }

  @Override
  @NotNull
  public String getName() {
    return InspectionsBundle.message("annotate.overridden.methods.parameters", ClassUtil.extractClassName(myAnnotation));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();

    PsiParameter parameter = PsiTreeUtil.getParentOfType(psiElement, PsiParameter.class, false);
    if (parameter == null) return;
    PsiMethod method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class);
    if (method == null) return;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    int index = ArrayUtilRt.find(parameters, parameter);

    List<PsiParameter> toAnnotate = new ArrayList<>();

    PsiMethod[] methods = OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);
    for (PsiMethod psiMethod : methods) {
      PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
      if (index >= psiParameters.length) continue;
      PsiParameter psiParameter = psiParameters[index];
      if (PsiManager.getInstance(project).isInProject(psiMethod) && AddAnnotationPsiFix.isAvailable(psiMethod, myAnnotation)) {
        toAnnotate.add(psiParameter);
      }
    }

    FileModificationService.getInstance().preparePsiElementsForWrite(toAnnotate);
    RuntimeException exception = null;
    for (PsiParameter psiParam : toAnnotate) {
      assert psiParam != null : toAnnotate;
      try {
        if (AnnotationUtil.isAnnotatingApplicable(psiParam, myAnnotation)) {
          AddAnnotationPsiFix fix = new AddAnnotationPsiFix(myAnnotation, psiParam, PsiNameValuePair.EMPTY_ARRAY, myAnnosToRemove);
          PsiFile containingFile = psiParam.getContainingFile();
          if (fix.isAvailable(project, containingFile, psiParam, psiParam)) {
            fix.invoke(project, containingFile, psiParam, psiParam);
          }
        }
      }
      catch (PsiInvalidElementAccessException|IncorrectOperationException e) {
        exception = e;
      }
      if (exception != null) {
        throw exception;
      }
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("annotate.overridden.methods.parameters.family.name");
  }
}

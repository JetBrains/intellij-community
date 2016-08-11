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
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class AnnotateMethodFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.AnnotateMethodFix");
  protected final String myAnnotation;
  private final String[] myAnnotationsToRemove;

  public AnnotateMethodFix(@NotNull String fqn, @NotNull String... annotationsToRemove) {
    myAnnotation = fqn;
    myAnnotationsToRemove = annotationsToRemove;
  }

  @Override
  @NotNull
  public String getName() {
    return InspectionsBundle.message("inspection.annotate.method.quickfix.name", ClassUtil.extractClassName(myAnnotation));
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();

    PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
    if (method == null) return;
    final List<PsiMethod> toAnnotate = new ArrayList<>();
    toAnnotate.add(method);
    List<MethodSignatureBackedByPsiMethod> superMethodSignatures = method.findSuperMethodSignaturesIncludingStatic(true);
    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      if (!AnnotationUtil.isAnnotated(superMethod, myAnnotation, false, false) && superMethod.getManager().isInProject(superMethod)) {
        int ret = shouldAnnotateBaseMethod(method, superMethod, project);
        if (ret != 0 && ret != 1) return;
        if (ret == 0) {
          toAnnotate.add(superMethod);
        }
      }
    }
    if (annotateOverriddenMethods()) {
      PsiMethod[] methods = OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);
      for (PsiMethod psiMethod : methods) {
        if (AnnotationUtil.isAnnotatingApplicable(psiMethod, myAnnotation) && !AnnotationUtil.isAnnotated(psiMethod, myAnnotation, false, false) && psiMethod.getManager().isInProject(psiMethod)) {
          toAnnotate.add(psiMethod);
        }
      }
    }

    FileModificationService.getInstance().preparePsiElementsForWrite(toAnnotate);
    for (PsiMethod psiMethod : toAnnotate) {
      annotateMethod(psiMethod);
    }
    UndoUtil.markPsiFileForUndo(method.getContainingFile());
  }

  // 0-annotate, 1-do not annotate, 2- cancel
  public int shouldAnnotateBaseMethod(final PsiMethod method, final PsiMethod superMethod, final Project project) {
    return 0;
  }

  protected boolean annotateOverriddenMethods() {
    return false;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("inspection.annotate.method.quickfix.family.name");
  }

  private void annotateMethod(@NotNull PsiMethod method) {
    try {
      AddAnnotationPsiFix fix = new AddAnnotationPsiFix(myAnnotation, method, PsiNameValuePair.EMPTY_ARRAY, myAnnotationsToRemove);
      fix.invoke(method.getProject(), method.getContainingFile(), method, method);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}

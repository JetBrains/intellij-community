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
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author cdr
 */
public class AnnotateMethodFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance(AnnotateMethodFix.class);

  protected final String myAnnotation;
  private final String[] myAnnotationsToRemove;

  public AnnotateMethodFix(@NotNull String fqn, @NotNull String... annotationsToRemove) {
    myAnnotation = fqn;
    myAnnotationsToRemove = annotationsToRemove;
    LOG.assertTrue(annotateSelf() || annotateOverriddenMethods(), "annotate method quick fix should not do nothing");
  }

  @Override
  @NotNull
  public String getName() {
    return getFamilyName() + " " + InspectionsBundle.message("inspection.annotate.method.name.suffix", ClassUtil.extractClassName(myAnnotation));
  }

  @Override
  @NotNull
  public String getFamilyName() {
    if (annotateSelf()) {
      if (annotateOverriddenMethods()) {
        return InspectionsBundle.message("inspection.annotate.overridden.method.and.self.quickfix.family.name");
      } else {
        return InspectionsBundle.message("inspection.annotate.method.quickfix.family.name");
      }
    } else {
      return InspectionsBundle.message("inspection.annotate.overridden.method.quickfix.family.name");
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();

    PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
    if (method == null) return;
    final List<PsiMethod> toAnnotate = new ArrayList<>();
    if (annotateSelf()) {
      toAnnotate.add(method);
    }
    if (annotateOverriddenMethods()) {
      PsiMethod[] methods = OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);
      for (PsiMethod psiMethod : methods) {
        if (AnnotationUtil.isAnnotatingApplicable(psiMethod, myAnnotation) &&
            !AnnotationUtil.isAnnotated(psiMethod, myAnnotation, false, false, true) &&
            psiMethod.getManager().isInProject(psiMethod)) {
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

  protected boolean annotateOverriddenMethods() {
    return false;
  }

  protected boolean annotateSelf() {
    return true;
  }

  private void annotateMethod(@NotNull PsiMethod method) {
    AddAnnotationPsiFix fix = new AddAnnotationPsiFix(myAnnotation, method, PsiNameValuePair.EMPTY_ARRAY, myAnnotationsToRemove);
    fix.invoke(method.getProject(), method.getContainingFile(), method, method);
  }
}

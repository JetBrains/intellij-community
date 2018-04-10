// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE;

/**
 * @author cdr
 */
public class AnnotateMethodFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance(AnnotateMethodFix.class);

  private final String myAnnotation;
  private final String[] myAnnotationsToRemove;

  public AnnotateMethodFix(@NotNull String fqn, @NotNull String... annotationsToRemove) {
    myAnnotation = fqn;
    myAnnotationsToRemove = annotationsToRemove.length == 0 ? ArrayUtil.EMPTY_STRING_ARRAY : annotationsToRemove;
    LOG.assertTrue(annotateSelf() || annotateOverriddenMethods(), "annotate method quick fix should not do nothing");
  }

  @Override
  @NotNull
  public String getName() {
    return getFamilyName() + " " + getPreposition() + " \'@" + ClassUtil.extractClassName(myAnnotation) + "\'";
  }

  @NotNull
  protected String getPreposition() {
    return "with";
  }

  @Override
  @NotNull
  public String getFamilyName() {
    if (annotateSelf()) {
      if (annotateOverriddenMethods()) {
        return InspectionsBundle.message("inspection.annotate.overridden.method.and.self.quickfix.family.name");
      }
      return InspectionsBundle.message("inspection.annotate.method.quickfix.family.name");
    }
    return InspectionsBundle.message("inspection.annotate.overridden.method.quickfix.family.name");
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

    if (annotateOverriddenMethods() && !ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      PsiMethod[] methods = OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY);
      for (PsiMethod psiMethod : methods) {
        ReadAction.run(() -> {
          if (psiMethod.isPhysical() &&
              psiMethod.getManager().isInProject(psiMethod) &&
              AnnotationUtil.isAnnotatingApplicable(psiMethod, myAnnotation) &&
              !AnnotationUtil.isAnnotated(psiMethod, myAnnotation, CHECK_EXTERNAL | CHECK_TYPE)) {
            toAnnotate.add(psiMethod);
          }
        });
      }
    }, "Searching for Overriding Methods", true, project)) {
      return;
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

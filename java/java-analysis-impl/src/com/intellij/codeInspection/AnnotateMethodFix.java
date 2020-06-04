// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.nullable.NullableStuffInspectionBase;
import com.intellij.java.analysis.JavaAnalysisBundle;
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
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE;

public class AnnotateMethodFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance(AnnotateMethodFix.class);

  private final String myAnnotation;
  private final String[] myAnnotationsToRemove;

  public AnnotateMethodFix(@NotNull String fqn, String @NotNull ... annotationsToRemove) {
    myAnnotation = fqn;
    myAnnotationsToRemove = annotationsToRemove.length == 0 ? ArrayUtilRt.EMPTY_STRING_ARRAY : annotationsToRemove;
    LOG.assertTrue(annotateSelf() || annotateOverriddenMethods(), "annotate method quick fix should not do nothing");
  }

  @Override
  @NotNull
  public String getName() {
    return getFamilyName() + " " + getPreposition() + " '@" + ClassUtil.extractClassName(myAnnotation) + "'";
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
        return JavaAnalysisBundle.message("inspection.annotate.overridden.method.and.self.quickfix.family.name");
      }
      return JavaAnalysisBundle.message("inspection.annotate.method.quickfix.family.name");
    }
    return JavaAnalysisBundle.message("inspection.annotate.overridden.method.quickfix.family.name");
  }

  @Override
  public boolean startInWriteAction() {
    return !annotateOverriddenMethods();
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

    if (annotateOverriddenMethods() && !processModifiableInheritorsUnderProgress(method, psiMethod -> {
      if (AnnotationUtil.isAnnotatingApplicable(psiMethod, myAnnotation) &&
          !AnnotationUtil.isAnnotated(psiMethod, myAnnotation, CHECK_EXTERNAL | CHECK_TYPE)) {
        toAnnotate.add(psiMethod);
      }
    })) {
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

  public static boolean processModifiableInheritorsUnderProgress(@NotNull PsiMethod method, @NotNull Consumer<? super PsiMethod> consumer) {
    return ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      for (PsiMethod psiMethod : OverridingMethodsSearch.search(method)) {
        ReadAction.run(() -> {
          if (psiMethod.isPhysical() && !NullableStuffInspectionBase.shouldSkipOverriderAsGenerated(psiMethod)) {
            consumer.accept(psiMethod);
          }
        });
      }
    }, JavaAnalysisBundle.message("searching.for.overriding.methods"), true, method.getProject());
  }
}

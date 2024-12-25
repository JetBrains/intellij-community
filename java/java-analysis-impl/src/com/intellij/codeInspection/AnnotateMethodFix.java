// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE;

/**
 * Annotate method in code only, not externally
 */
public class AnnotateMethodFix extends ModCommandQuickFix {
  private static final Logger LOG = Logger.getInstance(AnnotateMethodFix.class);

  protected final String myAnnotation;
  private final boolean myAnnotateOverriddenMethods;
  private final boolean myAnnotateSelf;
  private final String[] myAnnotationsToRemove;

  @SuppressWarnings("unused") // used in third-party plugins
  public AnnotateMethodFix(@NotNull String fqn, String @NotNull ... annotationsToRemove) {
    this(fqn, false, true, annotationsToRemove);
  }
  
  public AnnotateMethodFix(@NotNull String fqn, boolean annotateOverriddenMethods, boolean annotateSelf, String @NotNull ... annotationsToRemove) {
    myAnnotation = fqn;
    myAnnotateOverriddenMethods = annotateOverriddenMethods;
    myAnnotateSelf = annotateSelf;
    myAnnotationsToRemove = annotationsToRemove.length == 0 ? ArrayUtilRt.EMPTY_STRING_ARRAY : annotationsToRemove;
    LOG.assertTrue(myAnnotateSelf || myAnnotateOverriddenMethods, "annotate method quick fix should not do nothing");
  }

  @Override
  public @NotNull String getName() {
    if (myAnnotateSelf) {
      if (myAnnotateOverriddenMethods) {
        return JavaAnalysisBundle.message("inspection.annotate.overridden.method.and.self.quickfix.name",
                                          ClassUtil.extractClassName(myAnnotation));
      }
      return JavaAnalysisBundle.message("inspection.annotate.method.quickfix.name", ClassUtil.extractClassName(myAnnotation));
    }
    return JavaAnalysisBundle.message("inspection.annotate.overridden.method.quickfix.name",
                                      ClassUtil.extractClassName(myAnnotation));
  }

  @Override
  public @NotNull String getFamilyName() {
    if (myAnnotateSelf) {
      if (myAnnotateOverriddenMethods) {
        return JavaAnalysisBundle.message("inspection.annotate.overridden.method.and.self.quickfix.family.name");
      }
      return JavaAnalysisBundle.message("inspection.annotate.method.quickfix.family.name");
    }
    return JavaAnalysisBundle.message("inspection.annotate.overridden.method.quickfix.family.name");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();

    PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
    if (method == null) return ModCommand.nop();
    final List<PsiMethod> toAnnotate = new ArrayList<>();
    if (myAnnotateSelf) {
      toAnnotate.add(method);
    }

    if (myAnnotateOverriddenMethods) {
      for (PsiMethod inheritor : OverridingMethodsSearch.search(method)) {
        if (AnnotationUtil.isAnnotatingApplicable(inheritor, myAnnotation) &&
            !AnnotationUtil.isAnnotated(inheritor, myAnnotation, CHECK_EXTERNAL | CHECK_TYPE)) {
          toAnnotate.add(inheritor);
        }
      }
    }

    return ModCommand.psiUpdate(ActionContext.from(descriptor), updater -> {
      for (PsiMethod psiMethod : ContainerUtil.map(toAnnotate, updater::getWritable)) {
        annotateMethod(psiMethod);
      }
    });
  }

  private void annotateMethod(@NotNull PsiMethod method) {
    PsiModifierList list = method.getModifierList();
    if (myAnnotationsToRemove.length > 0) {
      for (PsiAnnotation annotation : list.getAnnotations()) {
        for (String fqn : myAnnotationsToRemove) {
          if (annotation.hasQualifiedName(fqn)) {
            annotation.delete();
          }
        }
      }
    }
    list.addAnnotation(myAnnotation);
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInspection.deprecation.DeprecationInspectionBase;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * This class can be extended by inspections that should report usage of elements annotated with some particular annotation(s).
 */
public abstract class AnnotatedElementInspectionBase extends LocalInspectionTool {
  public boolean myIgnoreInsideImports = true;


  @NotNull
  protected abstract List<String> getAnnotations();

  protected abstract void createProblem(@NotNull PsiReference reference,
                                        @NotNull PsiModifierListOwner annotatedTarget,
                                        @NotNull List<PsiAnnotation> annotations,
                                        @NotNull ProblemsHolder holder);

  protected boolean shouldProcessElement(@NotNull PsiModifierListOwner element) {
    return isLibraryElement(element);
  }

  @NotNull
  @Override
  public JPanel createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.ignore.inside.imports"), this, "myIgnoreInsideImports");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!isApplicable(holder.getFile(), holder.getProject())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new AnnotatedElementVisitorBase(myIgnoreInsideImports, getAnnotations()) {
      @Override
      public void processAnnotatedTarget(@NotNull PsiReference reference,
                                         @NotNull PsiModifierListOwner annotatedTarget,
                                         @NotNull List<PsiAnnotation> annotations) {
        if (AnnotatedElementInspectionBase.this.shouldProcessElement(annotatedTarget)) {
          createProblem(reference, annotatedTarget, annotations, holder);
        }
      }
    };
  }

  private boolean isApplicable(@Nullable PsiFile file, @Nullable Project project) {
    if (file == null || project == null) {
      return false;
    }

    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope = file.getResolveScope();
    for (String annotation : getAnnotations()) {
      if (javaPsiFacade.findClass(annotation, scope) != null) {
        return true;
      }
    }

    return false;
  }

  protected static String getPresentableText(@NotNull PsiElement psiElement) {
    return DeprecationInspectionBase.getPresentableName(psiElement);
  }

  private static boolean isLibraryElement(@NotNull PsiElement element) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return true;
    }
    VirtualFile containingVirtualFile = PsiUtilCore.getVirtualFile(element);
    return containingVirtualFile != null && ProjectFileIndex.getInstance(element.getProject()).isInLibraryClasses(containingVirtualFile);
  }
}
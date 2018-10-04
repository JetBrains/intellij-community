// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReference;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class UnstableApiUsageInspection extends AnnotatedElementInspectionBase {
  public final List<String> unstableApiAnnotations = new ExternalizableStringSet(
    "org.jetbrains.annotations.ApiStatus.Experimental",
    "com.google.common.annotations.Beta",
    "io.reactivex.annotations.Beta",
    "io.reactivex.annotations.Experimental",
    "rx.annotations.Experimental",
    "rx.annotations.Beta",
    "org.apache.http.annotation.Beta",
    "org.gradle.api.Incubating"
  );

  @NotNull
  @Override
  protected List<String> getAnnotations() {
    return unstableApiAnnotations;
  }

  @Override
  protected void createProblem(@NotNull PsiReference reference, @NotNull ProblemsHolder holder) {
    String message = JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.description", getReferenceText(reference));
    holder.registerProblem(reference, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  @Override
  protected boolean shouldProcessElement(@NotNull PsiModifierListOwner element) {
    return isLibraryElement(element);
  }

  @NotNull
  @Override
  public JPanel createOptionsPanel() {
    JPanel checkboxPanel = super.createOptionsPanel();

    //TODO in add annotation window "Include non-project items" should be enabled by default
    JPanel annotationsListControl = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      unstableApiAnnotations, JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.annotations.list"));

    JPanel panel = new JPanel(new BorderLayout(2, 2));
    panel.add(checkboxPanel, BorderLayout.NORTH);
    panel.add(annotationsListControl, BorderLayout.CENTER);
    return panel;
  }

  private static boolean isLibraryElement(@NotNull PsiElement element) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return true;
    }

    PsiFile containingPsiFile = element.getContainingFile();
    if (containingPsiFile == null) {
      return false;
    }
    VirtualFile containingVirtualFile = containingPsiFile.getVirtualFile();
    if (containingVirtualFile == null) {
      return false;
    }
    return ProjectFileIndex.getInstance(element.getProject()).isInLibraryClasses(containingVirtualFile);
  }
}

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.codeInspection.deprecation.DeprecationInspectionBase.getPresentableName;

public class UnstableApiUsageInspection extends AnnotatedElementInspectionBase {
  public final List<String> unstableApiAnnotations = new ExternalizableStringSet(
    "org.jetbrains.annotations.ApiStatus.Experimental",
    "org.jetbrains.annotations.ApiStatus.Internal",
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

  @NotNull
  @Override
  protected AnnotatedApiUsageProcessor buildAnnotatedApiUsageProcessor(@NotNull ProblemsHolder holder) {
    return new AnnotatedApiUsageProcessor() {
      @Override
      public void processAnnotatedTarget(@NotNull UElement sourceNode,
                                         @NotNull PsiModifierListOwner annotatedTarget,
                                         @NotNull List<? extends PsiAnnotation> annotations) {
        if (!AnnotatedElementInspectionBase.isLibraryElement(annotatedTarget)) {
          return;
        }
        String message = JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.description", getPresentableName(annotatedTarget));
        PsiElement elementToHighlight = sourceNode.getSourcePsi();
        if (elementToHighlight != null) {
          holder.registerProblem(elementToHighlight, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
      }
    };
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
}

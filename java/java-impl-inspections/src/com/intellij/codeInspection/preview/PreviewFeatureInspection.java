// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.preview;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.analysis.PreviewFeatureVisitorBase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public final class PreviewFeatureInspection extends LocalInspectionTool {

  @Override
  public @NotNull String getID() {
    return "preview";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly) {
    if (!PsiUtil.getLanguageLevel(holder.getFile()).isPreview()) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return new PreviewFeatureInspectionVisitor(holder);
  }

  private static final class PreviewFeatureInspectionVisitor extends PreviewFeatureVisitorBase {

    private final ProblemsHolder myHolder;

    private PreviewFeatureInspectionVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    protected void registerProblem(PsiElement element, String description, HighlightingFeature feature, PsiAnnotation annotation) {
      myHolder.registerProblem(element, description);
    }
  }
}

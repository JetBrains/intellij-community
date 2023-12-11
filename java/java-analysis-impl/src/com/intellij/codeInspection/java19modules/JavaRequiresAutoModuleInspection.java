// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19modules;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class JavaRequiresAutoModuleInspection extends AbstractBaseJavaLocalInspectionTool {
  public boolean TRANSITIVE_ONLY = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("TRANSITIVE_ONLY", JavaAnalysisBundle.message("inspection.requires.auto.module.option")));
  }

  @Override
  public @Nullable String getAlternativeID() {
    return "JavaRequiresAutoModule";
  }

  @Override
  public @NotNull String getID() {
    return "requires-transitive-automatic";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return !PsiUtil.isModuleFile(holder.getFile()) ? PsiElementVisitor.EMPTY_VISITOR : new JavaElementVisitor() {
      @Override
      public void visitRequiresStatement(@NotNull PsiRequiresStatement statement) {
        super.visitRequiresStatement(statement);
        PsiJavaModuleReferenceElement refElement = statement.getReferenceElement();
        if (refElement != null) {
          PsiJavaModule target = statement.resolve();
          if (target instanceof LightJavaModule) {
            if (!TRANSITIVE_ONLY) {
              holder.registerProblem(refElement, JavaAnalysisBundle.message("inspection.requires.auto.module.message"));
            }
            else if (statement.hasModifierProperty(PsiModifier.TRANSITIVE)) {
              holder.registerProblem(refElement, JavaAnalysisBundle.message("inspection.requires.auto.module.transitive"));
            }
          }
        }
      }
    };
  }
}
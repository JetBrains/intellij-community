// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.java19modules;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JavaRequiresAutoModuleInspection extends AbstractBaseJavaLocalInspectionTool {
  public boolean TRANSITIVE_ONLY = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(JavaAnalysisBundle.message("inspection.requires.auto.module.option"), this, "TRANSITIVE_ONLY");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return !PsiUtil.isModuleFile(holder.getFile()) ? PsiElementVisitor.EMPTY_VISITOR : new JavaElementVisitor() {
      @Override
      public void visitRequiresStatement(PsiRequiresStatement statement) {
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
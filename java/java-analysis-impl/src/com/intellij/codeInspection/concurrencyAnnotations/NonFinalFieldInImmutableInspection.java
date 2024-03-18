// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.concurrencyAnnotations;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public final class NonFinalFieldInImmutableInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.concurrency.annotation.issues");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "NonFinalFieldInImmutable";
  }


  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitField(@NotNull PsiField field) {
        super.visitField(field);
        if (field.hasModifierProperty(PsiModifier.FINAL)) {
          return;
        }
        final PsiClass containingClass = field.getContainingClass();
        if (containingClass != null) {
          if (!JCiPUtil.isImmutable(containingClass)) {
            return;
          }
          holder.registerProblem(field.getNameIdentifier(),
                                 JavaAnalysisBundle.message("non.final.field.code.ref.code.in.immutable.class.loc"));
        }
      }
    };
  }
}
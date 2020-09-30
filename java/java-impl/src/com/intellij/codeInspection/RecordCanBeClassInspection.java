// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class RecordCanBeClassInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.RECORDS.isAvailable(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitClass(PsiClass aClass) {
        if (aClass.isRecord()) {
          if (InspectionProjectProfileManager.isInformationLevel(getShortName(), aClass)) {
            PsiElement brace = aClass.getLBrace();
            if (brace != null) {
              holder.registerProblem(aClass, TextRange.create(0, brace.getStartOffsetInParent() + brace.getTextLength()),
                                     JavaBundle.message("inspection.message.record.can.be.converted.to.class"),
                                     new ConvertRecordToClassFix(aClass));
            }
            else {
              holder.registerProblem(aClass, JavaBundle.message("inspection.message.record.can.be.converted.to.class"),
                                     new ConvertRecordToClassFix(aClass));
            }
          }
          else {
            PsiIdentifier identifier = aClass.getNameIdentifier();
            if (identifier != null) {
              holder.registerProblem(identifier, JavaBundle.message("inspection.message.record.can.be.converted.to.class"),
                                     new ConvertRecordToClassFix(aClass));
            }
          }
        }
      }
    };
  }
}

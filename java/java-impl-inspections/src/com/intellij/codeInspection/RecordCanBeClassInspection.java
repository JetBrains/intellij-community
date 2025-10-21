// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class RecordCanBeClassInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.RECORDS);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        if (aClass.isRecord()) {
          if (InspectionProjectProfileManager.isInformationLevel(getShortName(), aClass)) {
            PsiElement brace = aClass.getLBrace();
            if (brace != null) {
              holder.problem(aClass, JavaBundle.message("inspection.message.record.can.be.converted.to.class"))
                .range(TextRange.create(0, brace.getStartOffsetInParent() + brace.getTextLength()))
                .fix(new ConvertRecordToClassFix(aClass)).register();
            }
            else {
              holder.problem(aClass, JavaBundle.message("inspection.message.record.can.be.converted.to.class"))
                .fix(new ConvertRecordToClassFix(aClass)).register();
            }
          }
          else {
            PsiIdentifier identifier = aClass.getNameIdentifier();
            if (identifier != null) {
              holder.problem(identifier, JavaBundle.message("inspection.message.record.can.be.converted.to.class"))
                .fix(new ConvertRecordToClassFix(aClass)).register();
            }
          }
        }
      }
    };
  }
}

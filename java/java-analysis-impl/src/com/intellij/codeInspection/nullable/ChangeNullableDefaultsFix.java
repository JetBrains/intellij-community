// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.modcommand.ModCommand.*;

class ChangeNullableDefaultsFix extends ModCommandQuickFix {
  private final String myNotNullName;
  private final String myNullableName;

  ChangeNullableDefaultsFix(@Nullable PsiAnnotation notNull, @Nullable PsiAnnotation nullable) {
    myNotNullName = notNull != null ? notNull.getQualifiedName() : null;
    myNullableName = nullable != null ? nullable.getQualifiedName() : null;
  }

  ChangeNullableDefaultsFix(String notNull, String nullable) {
    myNotNullName = notNull;
    myNullableName = nullable;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("make.0.default.annotation", myNotNullName != null ? myNotNullName : myNullableName);
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    ModCommand command = nop();
    if (myNotNullName != null) {
      command = command.andThen(
        updateOptionList(descriptor.getPsiElement(), "NullableNotNullManager.myNotNulls", list -> {
          if (!list.contains(myNotNullName)) {
            list.add(myNotNullName);
          }
        })).andThen(updateOption(descriptor.getPsiElement(), "NullableNotNullManager.myDefaultNotNull", myNotNullName));
    }
    else {
      command = command.andThen(
        updateOptionList(descriptor.getPsiElement(), "NullableNotNullManager.myNullables", list -> {
          if (!list.contains(myNullableName)) {
            list.add(myNullableName);
          }
        })).andThen(updateOption(descriptor.getPsiElement(), "NullableNotNullManager.myDefaultNullable", myNullableName));
    }
    return command;
  }
}

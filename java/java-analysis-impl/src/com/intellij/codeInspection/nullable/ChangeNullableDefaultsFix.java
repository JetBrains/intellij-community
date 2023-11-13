/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.nullable;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.modcommand.ModCommand.nop;
import static com.intellij.modcommand.ModCommand.updateOption;

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

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaAnalysisBundle.message("make.0.default.annotation", myNotNullName != null ? myNotNullName : myNullableName);
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    ModCommand command = nop();
    if (myNotNullName != null) {
      command = command.andThen(
        updateOption(descriptor.getPsiElement(), "NullableNotNullManager.myNotNulls", old -> {
          @SuppressWarnings("unchecked") List<String> list = (List<String>)old;
          if (!list.contains(myNotNullName)) {
            list.add(myNotNullName);
          }
          return list;
        })).andThen(
        updateOption(descriptor.getPsiElement(), "NullableNotNullManager.myDefaultNotNull", old -> myNotNullName));
    }
    else {
      command = command.andThen(
        updateOption(descriptor.getPsiElement(), "NullableNotNullManager.myNullables", old -> {
          @SuppressWarnings("unchecked") List<String> list = (List<String>)old;
          if (!list.contains(myNullableName)) {
            list.add(myNullableName);
          }
          return list;
        })).andThen(
        updateOption(descriptor.getPsiElement(), "NullableNotNullManager.myDefaultNullable", old -> myNotNullName));
    }
    return command;
  }
}

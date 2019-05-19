/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.compiler;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/** @deprecated use {@link com.intellij.codeInsight.intention.QuickFixFactory#createDeleteFix} (to be removed in IDEA 2018) */
@Deprecated
public class RemoveElementQuickFix implements LocalQuickFix {
  private final String myName;

  public RemoveElementQuickFix(@NotNull @Nls final String name) {
    myName = name;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return myName;
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (element != null) {
      element.delete();
    }
  }
}
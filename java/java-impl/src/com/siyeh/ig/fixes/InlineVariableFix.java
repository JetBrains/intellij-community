/*
 * Copyright 2003-2024 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.refactoring.util.CommonJavaInlineUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class InlineVariableFix extends ModCommandQuickFix {
  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("inline.variable.quickfix");
  }

  @Override
  public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiLocalVariable variable = (PsiLocalVariable)descriptor.getPsiElement().getParent();
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) {
      return ModCommand.nop();
    }
    var inlineUtil = CommonJavaInlineUtil.getInstance();
    return inlineUtil.inline(variable);
  }
}
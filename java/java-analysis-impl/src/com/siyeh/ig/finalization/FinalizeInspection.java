/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.finalization;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class FinalizeInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreTrivialFinalizers = true;

  @Override
  @NotNull
  public String getID() {
    return "FinalizeDeclaration";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "finalize.declaration.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreTrivialFinalizers", InspectionGadgetsBundle.message("ignore.trivial.finalizers.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FinalizeDeclaredVisitor();
  }

  private class FinalizeDeclaredVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!MethodUtils.isFinalize(method)) {
        return;
      }
      if (ignoreTrivialFinalizers && MethodUtils.isTrivial(method)) {
        return;
      }
      registerMethodError(method);
    }
  }
}
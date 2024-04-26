/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

public final class EmptyInitializerInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "EmptyClassInitializer";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "empty.class.initializer.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new EmptyInitializerFix();
  }

  private static class EmptyInitializerFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "empty.class.initializer.delete.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement codeBlock = element.getParent();
      if (!(codeBlock instanceof PsiCodeBlock)) return;
      final PsiElement classInitializer = codeBlock.getParent();
      if (!(classInitializer instanceof PsiClassInitializer)) return;
      classInitializer.delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EmptyInitializerVisitor();
  }

  private static class EmptyInitializerVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
      super.visitClassInitializer(initializer);
      final PsiCodeBlock body = initializer.getBody();
      if (!ControlFlowUtils.isEmptyCodeBlock(body)) {
        return;
      }
      registerClassInitializerError(initializer);
    }
  }
}
/*
 * Copyright 2003-2020 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class SynchronizedMethodInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_includeNativeMethods = true;

  @SuppressWarnings("PublicField")
  public boolean ignoreSynchronizedSuperMethods = true;

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiMethod method = (PsiMethod)infos[0];
    return InspectionGadgetsBundle.message(
      "synchronized.method.problem.descriptor", method.getName());
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiMethod method = (PsiMethod)infos[0];
    if (method.getBody() == null) {
      return null;
    }
    return new SynchronizedMethodFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SynchronizedMethodVisitor();
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_includeNativeMethods", InspectionGadgetsBundle.message("synchronized.method.include.option")),
      checkbox("ignoreSynchronizedSuperMethods", InspectionGadgetsBundle.message("synchronized.method.ignore.synchronized.super.option")));
  }

  private static class SynchronizedMethodFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "synchronized.method.move.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement nameElement, @NotNull ModPsiUpdater updater) {
      final PsiModifierList modifierList = (PsiModifierList)nameElement.getParent();
      assert modifierList != null;
      final PsiMethod method = (PsiMethod)modifierList.getParent();
      modifierList.setModifierProperty(PsiModifier.SYNCHRONIZED, false);
      assert method != null;
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      final String text = body.getText();
      @NonNls final String replacementText;
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiClass containingClass = method.getContainingClass();
        assert containingClass != null;
        final String className = containingClass.getName();
        replacementText = "{ synchronized(" + className + ".class)" + text + '}';
      }
      else {
        replacementText = "{ synchronized(this)" + text + '}';
      }
      final PsiCodeBlock block = JavaPsiFacade.getElementFactory(project).createCodeBlockFromText(replacementText, method);
      body.replace(block);
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      codeStyleManager.reformat(method);
    }
  }

  private class SynchronizedMethodVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
        return;
      }
      if (!m_includeNativeMethods && method.hasModifierProperty(PsiModifier.NATIVE)) {
        return;
      }
      if (ignoreSynchronizedSuperMethods) {
        final PsiMethod[] superMethods = method.findSuperMethods();
        for (final PsiMethod superMethod : superMethods) {
          if (superMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
            return;
          }
        }
      }
      registerModifierError(PsiModifier.SYNCHRONIZED, method, method);
    }
  }
}
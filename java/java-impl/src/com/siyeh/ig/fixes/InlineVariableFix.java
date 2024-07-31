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

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.util.CommonJavaInlineUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public class InlineVariableFix extends PsiUpdateModCommandQuickFix {

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("inline.variable.quickfix");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement nameElement, @NotNull ModPsiUpdater updater) {
    final PsiLocalVariable variable = (PsiLocalVariable)nameElement.getParent();
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) {
      return;
    }

    final Collection<PsiReferenceExpression> references = VariableAccessUtils.getVariableReferences(variable);
    final Collection<PsiElement> replacedElements = new ArrayList<>();
    for (PsiReferenceExpression reference : references) {
      var inlineUtil = CommonJavaInlineUtil.getInstance();
      final PsiExpression expression = inlineUtil.inlineVariable(variable, initializer, reference, null);
      replacedElements.add(expression);
    }

    new CommentTracker().deleteAndRestoreComments(variable);
    boolean positioned = false;
    for (PsiElement element : replacedElements) {
      if (element.isValid()) {
        updater.highlight(element);
        if (!positioned) {
          positioned = true;
          updater.moveCaretTo(element);
        }
      }
    }
  }
}
/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.impl.analysis.SwitchBlockHighlightingModel.PatternsInSwitchBlockHighlightingModel.CompletenessResult;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiSwitchStatement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.CreateDefaultBranchFix;
import com.siyeh.ig.psiutils.SwitchUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.daemon.impl.analysis.SwitchBlockHighlightingModel.PatternsInSwitchBlockHighlightingModel;
import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class SwitchStatementsWithoutDefaultInspection extends AbstractBaseJavaLocalInspectionTool {

  /**
   * This option covers not only enums, but sealed classes as well
   */
  @SuppressWarnings("PublicField")
  public boolean m_ignoreFullyCoveredEnums = true;

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "SwitchStatementWithoutDefaultBranch";
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreFullyCoveredEnums", InspectionGadgetsBundle.message("switch.statement.without.default.ignore.option")));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      // handling switch expression seems unnecessary here as non-exhaustive switch expression
      // without default is a compilation error
      @Override
      public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
        super.visitSwitchStatement(statement);
        final int count = SwitchUtils.calculateBranchCount(statement);
        if (count < 0) return;
        PsiCodeBlock body = statement.getBody();
        if (body == null || body.getRBrace() == null) return;
        boolean infoMode = false;
        if (count == 0) {
          if (!isOnTheFly) return;
          infoMode = true;
        }
        else {
          CompletenessResult completenessResult = PatternsInSwitchBlockHighlightingModel.evaluateSwitchCompleteness(statement);
          if (completenessResult == CompletenessResult.UNEVALUATED || completenessResult == CompletenessResult.COMPLETE_WITH_UNCONDITIONAL) return;
          if (m_ignoreFullyCoveredEnums && completenessResult == CompletenessResult.COMPLETE_WITHOUT_UNCONDITIONAL) {
            if (!isOnTheFly) return;
            infoMode = true;
          }
        }
        String message = InspectionGadgetsBundle.message("switch.statements.without.default.problem.descriptor");
        if (infoMode || (isOnTheFly && InspectionProjectProfileManager.isInformationLevel(getShortName(), statement))) {
          holder.problem(statement, message).highlight(ProblemHighlightType.INFORMATION)
            .fix(new CreateDefaultBranchFix(statement, null)).register();
        }
        else {
          holder.problem(statement.getFirstChild(), message).fix(new CreateDefaultBranchFix(statement, null)).register();
        }
      }
    };
  }
}
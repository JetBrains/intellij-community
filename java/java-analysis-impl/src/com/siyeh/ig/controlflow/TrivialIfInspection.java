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

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.UpdateInspectionOptionFix;
import com.intellij.codeInspection.options.OptCheckbox;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.style.ConditionalExpressionGenerator;
import com.siyeh.ig.style.IfConditionalModel;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class TrivialIfInspection extends BaseInspection implements CleanupLocalInspectionTool {

  public boolean ignoreChainedIf = false;
  public boolean ignoreAssertStatements = false;

  @Pattern(VALID_ID_PATTERN)
  @Override
  public @NotNull String getID() {
    return "RedundantIfStatement";
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreChainedIf", InspectionGadgetsBundle.message("trivial.if.option.ignore.chained")),
      checkbox("ignoreAssertStatements", InspectionGadgetsBundle.message("trivial.if.option.ignore.assert.statements"))
    );
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("trivial.if.problem.descriptor");
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    List<LocalQuickFix> fixes = new SmartList<>(new TrivialIfFix());
    String turnOffOption = (String)infos[0];
    if (turnOffOption != null) {
      OptCheckbox checkbox = (OptCheckbox)Objects.requireNonNull(getOptionsPane().findControl(turnOffOption));
      String message = StringUtil.unescapeXmlEntities(checkbox.label().label());
      fixes.add(LocalQuickFix.from(new UpdateInspectionOptionFix(this, turnOffOption, message, true)));
    }
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  private static class TrivialIfFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("trivial.if.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement ifKeywordElement, @NotNull ModPsiUpdater updater) {
      final PsiIfStatement statement = (PsiIfStatement)ifKeywordElement.getParent();
      simplify(statement);
    }
  }

  private static void simplify(PsiIfStatement statement) {
    IfConditionalModel model = IfConditionalModel.from(statement, true);
    if (model != null) {
      ConditionalExpressionGenerator generator = ConditionalExpressionGenerator.from(model);
      if (generator != null) {
        CommentTracker ct = new CommentTracker();
        String text = generator.generate(ct);
        if (model.getElseExpression().textMatches(text) && !PsiTreeUtil.isAncestor(statement, model.getElseBranch(), false)) {
          ct.deleteAndRestoreComments(statement);
        } else if (model.getElseBranch() instanceof PsiDeclarationStatement){
          ct.replace(model.getElseExpression(), text);
          ct.deleteAndRestoreComments(statement);
        } else {
          ct.replace(model.getThenExpression(), text);
          ct.replaceAndRestoreComments(statement, model.getThenBranch());
          PsiStatement elseBranch = model.getElseBranch();
          if (elseBranch.isValid() && (elseBranch instanceof PsiExpressionStatement || !ControlFlowUtils.isReachable(elseBranch))) {
            PsiElement sibling = elseBranch.getPrevSibling();
            if (sibling instanceof PsiWhiteSpace) {
              sibling.delete();
            }
            new CommentTracker().deleteAndRestoreComments(elseBranch);
          }
        }
      }
    }
    if (isSimplifiableAssert(statement)) {
      replaceSimplifiableAssert(statement);
    }
  }

  private static void replaceSimplifiableAssert(PsiIfStatement statement) {
    final PsiExpression condition = statement.getCondition();
    if (condition == null) {
      return;
    }
    final String conditionText = BoolUtils.getNegatedExpressionText(condition);
    if (statement.getElseBranch() != null) {
      return;
    }
    final PsiStatement thenBranch = ControlFlowUtils.stripBraces(statement.getThenBranch());
    if (!(thenBranch instanceof PsiAssertStatement assertStatement)) {
      return;
    }
    final PsiExpression assertCondition = assertStatement.getAssertCondition();
    if (assertCondition == null) {
      return;
    }
    final PsiExpression replacementCondition = JavaPsiFacade.getElementFactory(statement.getProject()).createExpressionFromText(
      BoolUtils.isFalse(assertCondition) ? conditionText : conditionText + "||" + assertCondition.getText(), statement);
    assertCondition.replace(replacementCondition);
    statement.replace(assertStatement);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitIfStatement(@NotNull PsiIfStatement ifStatement) {
        super.visitIfStatement(ifStatement);
        boolean chainedIf = PsiTreeUtil.skipWhitespacesAndCommentsBackward(ifStatement) instanceof PsiIfStatement ||
                            (ifStatement.getParent() instanceof PsiIfStatement &&
                             ((PsiIfStatement)ifStatement.getParent()).getElseBranch() == ifStatement);
        if (ignoreChainedIf && chainedIf && !isOnTheFly()) return;
        final PsiExpression condition = ifStatement.getCondition();
        if (condition == null) {
          return;
        }
        if (isTrivial(ifStatement)) {
          PsiElement anchor = Objects.requireNonNull(ifStatement.getFirstChild());
          ProblemHighlightType level =
            ignoreChainedIf && chainedIf ? ProblemHighlightType.INFORMATION : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
          String turnOffOption;
          if (chainedIf && !ignoreChainedIf &&
              !InspectionProjectProfileManager.isInformationLevel(getShortName(), ifStatement)) {
            turnOffOption = "ignoreChainedIf";
          }
          else if (!ignoreAssertStatements && isSimplifiableAssert(ifStatement)) {
            turnOffOption = "ignoreAssertStatements";
          }
          else {
            turnOffOption = null;
          }
          registerError(anchor, level, turnOffOption);
        }
      }
    };
  }

  private boolean isTrivial(PsiIfStatement ifStatement) {
    if (PsiUtilCore.hasErrorElementChild(ifStatement)) {
      return false;
    }
    IfConditionalModel model = IfConditionalModel.from(ifStatement, true);
    if (model != null) {
      ConditionalExpressionGenerator generator = ConditionalExpressionGenerator.from(model);
      if (generator != null && generator.getTokenType().isEmpty()) return true;
    }

    return !ignoreAssertStatements && isSimplifiableAssert(ifStatement);
  }

  private static boolean isSimplifiableAssert(PsiIfStatement ifStatement) {
    if (ifStatement.getElseBranch() != null) {
      return false;
    }
    final PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
    if (!(thenBranch instanceof PsiAssertStatement assertStatement)) {
      return false;
    }
    return assertStatement.getAssertCondition() != null;
  }
}
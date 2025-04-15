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

import com.intellij.codeInsight.daemon.impl.quickfix.ConvertSwitchToIfIntention;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.UpdateInspectionOptionFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.*;

public final class SwitchStatementWithTooFewBranchesInspection extends BaseInspection {

  private static final int DEFAULT_BRANCH_LIMIT = 2;

  @SuppressWarnings("PublicField")
  public int m_limit = DEFAULT_BRANCH_LIMIT;

  public boolean ignorePatternSwitch = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("m_limit", InspectionGadgetsBundle.message("switch.statement.with.too.few.branches.min.option"), 1, 1000),
      checkbox("ignorePatternSwitch", InspectionGadgetsBundle.message("switch.statement.with.too.few.branches.ignore.pattern.option"))
    );
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final Integer branchCount = (Integer)infos[0];
    final PsiSwitchBlock block = (PsiSwitchBlock)infos[1];
    if (block instanceof PsiSwitchExpression) {
      return branchCount == 0
             ? InspectionGadgetsBundle.message("switch.expression.with.single.default.message")
             : InspectionGadgetsBundle.message("switch.expression.with.too.few.branches.problem.descriptor", branchCount);
    }
    return branchCount == 0
           ? InspectionGadgetsBundle.message("switch.statement.with.single.default.message")
           : InspectionGadgetsBundle.message("switch.statement.with.too.few.branches.problem.descriptor", branchCount);
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    boolean canFix = (Boolean)infos[2];
    boolean patternSwitch = (Boolean)infos[3];
    List<LocalQuickFix> fixes = new ArrayList<>();
    if (canFix) {
      final Integer branchCount = (Integer)infos[0];
      fixes.add(new UnwrapSwitchStatementFix(branchCount));
    }
    if (patternSwitch) {
      fixes.add(LocalQuickFix.from(new UpdateInspectionOptionFix(
        this, "ignorePatternSwitch",
        InspectionGadgetsBundle.message("switch.statement.with.too.few.branches.ignore.pattern.option"),
        true)));
    }
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  @Override
  public void writeSettings(@NotNull Element node) {
    defaultWriteSettings(node, "ignorePatternSwitch");
    writeBooleanOption(node, "ignorePatternSwitch", false);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MinimumSwitchBranchesVisitor();
  }

  /**
   * Unwraps switch expression if it consists of single expression-branch; does nothing otherwise
   *
   * @param switchExpression expression to unwrap
   */
  private static void unwrapExpression(@NotNull PsiSwitchExpression switchExpression) {
    PsiExpression expression = getOnlyExpression(switchExpression);
    if (expression == null) return;
    PsiExpression selector = switchExpression.getExpression();
    CommentTracker tracker = new CommentTracker();
    if (selector != null) {
      List<PsiExpression> expressions = SideEffectChecker.extractSideEffectExpressions(selector);
      if (!expressions.isEmpty()) {
        expressions.forEach(tracker::markUnchanged);
        PsiStatement[] sideEffects = StatementExtractor.generateStatements(expressions, selector);
        CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(switchExpression);
        if (surrounder != null) {
          CodeBlockSurrounder.SurroundResult result = surrounder.surround();
          PsiStatement anchor = result.getAnchor();
          for (PsiStatement effect : sideEffects) {
            anchor.getParent().addBefore(effect, anchor);
          }
          switchExpression = (PsiSwitchExpression)result.getExpression();
        }
      }
    }
    tracker.replaceAndRestoreComments(switchExpression, expression);
  }

  private static @Nullable PsiExpression getOnlyExpression(@NotNull PsiSwitchExpression switchExpression) {
    PsiCodeBlock body = switchExpression.getBody();
    if (body == null) return null;
    PsiStatement[] statements = body.getStatements();
    if (statements.length != 1 || !(statements[0] instanceof PsiSwitchLabeledRuleStatement rule)) return null;
    PsiStatement ruleBody = rule.getBody();
    if (!(ruleBody instanceof PsiExpressionStatement expressionStatement)) return null;
    return expressionStatement.getExpression();
  }

  private class MinimumSwitchBranchesVisitor extends BaseInspectionVisitor {
    @Override
    public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
      Object[] infos = processSwitch(expression);
      if (infos == null) return;
      registerError(expression.getFirstChild(), infos);
    }

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      Object[] infos = processSwitch(statement);
      if (infos == null) return;
      registerStatementError(statement, infos);
    }

    public Object @Nullable [] processSwitch(@NotNull PsiSwitchBlock block) {
      final PsiCodeBlock body = block.getBody();
      if (body == null) return null;
      final int branchCount = SwitchUtils.calculateBranchCount(block);
      int notDefaultBranches = branchCount < 0 ? -branchCount - 1 : branchCount;
      if (notDefaultBranches >= m_limit) {
        return null;
      }
      if (branchCount == 0) {
        // Empty switch is reported by another inspection
        return null;
      }
      boolean patternSwitch = ContainerUtil.exists(JavaPsiSwitchUtil.getSwitchBranches(block), e -> e instanceof PsiPattern);
      if (patternSwitch && ignorePatternSwitch) return null;
      if (branchCount > 0 && (patternSwitch || block instanceof PsiSwitchExpression)) {
        // Absence of 'default' branch makes the pattern-switch or expression-switch exhaustive
        // skip reporting in this case, as conversion to if-else or ?: will remove the compile-time
        // exhaustiveness check
        return null;
      }
      boolean fixIsAvailable;
      if (block instanceof PsiSwitchStatement) {
        fixIsAvailable = ConvertSwitchToIfIntention.isAvailable((PsiSwitchStatement)block);
      }
      else {
        PsiStatement[] statements = body.getStatements();
        if (statements.length == 1 && statements[0] instanceof PsiSwitchLabeledRuleStatement statement) {
          fixIsAvailable = SwitchUtils.isDefaultLabel(statement) &&
                           statement.getBody() instanceof PsiExpressionStatement &&
                           (block.getExpression() == null ||
                            !SideEffectChecker.mayHaveSideEffects(block.getExpression()) ||
                            CodeBlockSurrounder.canSurround(block.getExpression()));
        }
        else {
          fixIsAvailable = false;
        }
      }
      return new Object[]{Integer.valueOf(notDefaultBranches), block, fixIsAvailable, patternSwitch};
    }
  }

  public static final class UnwrapSwitchStatementFix extends PsiUpdateModCommandQuickFix {
    int myBranchCount;

    private UnwrapSwitchStatementFix(int branchCount) {
      myBranchCount = branchCount;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getName() {
      return myBranchCount == 0 ? getFamilyName() : CommonQuickFixBundle.message("fix.replace.x.with.y", JavaKeywords.SWITCH, JavaKeywords.IF);
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.unwrap", JavaKeywords.SWITCH);
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      PsiSwitchBlock block = PsiTreeUtil.getParentOfType(startElement, PsiSwitchBlock.class);
      if (block instanceof PsiSwitchStatement) {
        ConvertSwitchToIfIntention.doProcessIntention((PsiSwitchStatement)block);
      } else if (block instanceof PsiSwitchExpression) {
        unwrapExpression((PsiSwitchExpression)block);
      }
    }
  }
}

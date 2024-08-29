// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;

import static com.intellij.codeInsight.daemon.impl.analysis.PatternsInSwitchBlockHighlightingModel.CompletenessResult;
import static com.intellij.codeInsight.daemon.impl.analysis.PatternsInSwitchBlockHighlightingModel.evaluateSwitchCompleteness;
import static com.intellij.codeInspection.options.OptPane.*;
import static com.intellij.util.ObjectUtils.tryCast;

public final class EnhancedSwitchMigrationInspection extends AbstractBaseJavaLocalInspectionTool {
  @SuppressWarnings("WeakerAccess") public boolean myWarnOnlyOnExpressionConversion = true;
  @SuppressWarnings("WeakerAccess") public int myMaxNumberStatementsForBranch = 2;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("myWarnOnlyOnExpressionConversion", JavaBundle.message("inspection.switch.expression.migration.warn.only.on.expression")),
      number("myMaxNumberStatementsForBranch", JavaBundle.message("inspection.switch.expression.migration.expression.max.statements"),
             1, 200));
  }

  private static final SwitchConversion[] ourInspections = new SwitchConversion[]{
    (statement, branches, isExhaustive, maxNumberStatementsForExpression) -> inspectReturningSwitch(statement, branches, isExhaustive,
                                                                                                    maxNumberStatementsForExpression),
    (statement, branches, isExhaustive, maxNumberStatementsForExpression) -> inspectVariableAssigningSwitch(statement, branches,
                                                                                                            isExhaustive,
                                                                                                            maxNumberStatementsForExpression),
    (statement, branches, isExhaustive, maxNumberStatementsForExpression) -> inspectReplacementWithStatement(statement, branches)
  };

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.ENHANCED_SWITCH);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
        PsiElement switchKeyword = statement.getFirstChild();
        if (switchKeyword == null) {
          return;
        }
        List<SwitchReplacer> replacers = findSwitchReplacers(statement, myMaxNumberStatementsForBranch);
        if (replacers.isEmpty()) return;
        Optional<SwitchReplacer> replacerWithWarningLevel = replacers.stream().filter(t -> isWarningLevel(t)).findFirst();
        if (replacerWithWarningLevel.isPresent()) {
          SwitchReplacer replacer = replacerWithWarningLevel.get();
          List<LocalQuickFix> fixes = new ArrayList<>();
          fixes.add(new ReplaceWithSwitchExpressionFix(replacer.getType()));
          if (!myWarnOnlyOnExpressionConversion && replacer.getType() == ReplacementType.Statement) {
            fixes.add(LocalQuickFix.from(new UpdateInspectionOptionFix(
              EnhancedSwitchMigrationInspection.this, "myWarnOnlyOnExpressionConversion",
              JavaBundle.message("inspection.switch.expression.migration.warn.only.on.expression"),
              true)));
          }
          if (replacer.getType() == ReplacementType.Expression && replacer.getMaxNumberStatementsInBranch() != null && replacer.getMaxNumberStatementsInBranch() > 1) {
            int newMaxValue = replacer.getMaxNumberStatementsInBranch() - 1;
            fixes.add(LocalQuickFix.from(new UpdateInspectionOptionFix(
              EnhancedSwitchMigrationInspection.this, "myMaxNumberStatementsForBranch",
              JavaBundle.message("inspection.switch.expression.migration.option.expression.max.statements", newMaxValue),
              newMaxValue)));
          }
          holder.registerProblem(switchKeyword, JavaBundle.message("inspection.switch.expression.migration.inspection.switch.description"),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
          replacers.remove(replacer);
        }
        if (!holder.isOnTheFly()) {
          return;
        }
        if (replacers.isEmpty()) {
          return;
        }
        List<LocalQuickFix> fixes = ContainerUtil.map(replacers, replacer -> new ReplaceWithSwitchExpressionFix(replacer.getType()));
        holder.registerProblem(switchKeyword, JavaBundle.message("inspection.switch.expression.migration.inspection.switch.description"),
                               ProblemHighlightType.INFORMATION, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }

      private boolean isWarningLevel(@NotNull SwitchReplacer replacer) {
        if (replacer.isInformLevel()) {
          return false;
        }
        return !(myWarnOnlyOnExpressionConversion && replacer.getType() == ReplacementType.Statement);
      }
    };
  }

  private static List<SwitchReplacer> runInspections(@NotNull PsiStatement statement,
                                                     boolean isExhaustive,
                                                     @NotNull List<OldSwitchStatementBranch> branches,
                                                     int maxNumberStatementsForExpression) {
    List<SwitchReplacer> replacers = new ArrayList<>();
    for (SwitchConversion inspection : ourInspections) {
      SwitchReplacer replacer = inspection.suggestReplacer(statement, branches, isExhaustive, maxNumberStatementsForExpression);
      if (replacer != null) {
        replacers.add(replacer);
      }
    }
    return replacers;
  }

  private static OldSwitchStatementBranch addBranch(List<? super OldSwitchStatementBranch> branches,
                                                    PsiStatement[] statements,
                                                    int unmatchedCaseIndex,
                                                    int endIndexExcl,
                                                    boolean isFallthrough, PsiBreakStatement current) {
    PsiSwitchLabelStatement labelStatement = (PsiSwitchLabelStatement)statements[unmatchedCaseIndex];
    PsiStatement[] branchStatements = Arrays.copyOfRange(statements, unmatchedCaseIndex + 1, endIndexExcl);
    OldSwitchStatementBranch branch = new OldSwitchStatementBranch(isFallthrough, branchStatements, labelStatement, current);
    branches.add(branch);
    return branch;
  }

  private static @Nullable List<OldSwitchStatementBranch> extractBranches(@NotNull PsiCodeBlock body,
                                                                          PsiSwitchStatement switchStatement) {
    List<OldSwitchStatementBranch> branches = new ArrayList<>();
    PsiStatement[] statements = body.getStatements();
    int unmatchedCaseIndex = -1;
    OldSwitchStatementBranch previousBranch = null;
    for (int i = 0, length = statements.length; i < length; i++) {
      PsiStatement current = statements[i];
      if (current instanceof PsiSwitchLabelStatement) {
        if (unmatchedCaseIndex != -1) {
          boolean isFallthrough = i != 0 && ControlFlowUtils.statementMayCompleteNormally(statements[i - 1]);
          OldSwitchStatementBranch newBranch = addBranch(branches, statements, unmatchedCaseIndex, i, isFallthrough, null);
          newBranch.myPreviousSwitchBranch = previousBranch;
          previousBranch = newBranch;
        }
        unmatchedCaseIndex = i;
      }
      else if (current instanceof PsiBreakStatement) {
        if (((PsiBreakStatement)current).findExitedStatement() != switchStatement) return null;
        if (unmatchedCaseIndex == -1) return null;
        OldSwitchStatementBranch newBranch = addBranch(branches, statements, unmatchedCaseIndex, i, false, (PsiBreakStatement)current);
        newBranch.myPreviousSwitchBranch = previousBranch;
        previousBranch = newBranch;
        unmatchedCaseIndex = -1;
      }
      else if (current instanceof PsiSwitchLabeledRuleStatement) {
        return null;
      }
    }
    // tail
    if (unmatchedCaseIndex != -1) {
      OldSwitchStatementBranch branch = addBranch(branches, statements, unmatchedCaseIndex, statements.length, false, null);
      branch.myPreviousSwitchBranch = previousBranch;
    }
    return branches;
  }

  /**
   * Before using this method, make sure you are using a correct version of Java.
   */
  public static @Nullable SwitchReplacer findSwitchReplacer(@NotNull PsiSwitchStatement switchStatement) {
    List<SwitchReplacer> replacers = findSwitchReplacers(switchStatement, 1);
    for (SwitchReplacer replacer : replacers) {
      if (!replacer.isInformLevel()) {
        return replacer;
      }
    }
    return null;
  }

  private static @NotNull List<SwitchReplacer> findSwitchReplacers(@NotNull PsiSwitchStatement switchStatement,
                                                                   int maxNumberStatementsForExpression) {
    PsiExpression expression = switchStatement.getExpression();
    if (expression == null) return List.of();
    PsiCodeBlock body = switchStatement.getBody();
    if (body == null) return List.of();
    List<OldSwitchStatementBranch> branches = extractBranches(body, switchStatement);
    if (branches == null || branches.isEmpty()) return List.of();
    boolean isExhaustive = isExhaustiveSwitch(branches, switchStatement);
    return runInspections(switchStatement, isExhaustive, branches, maxNumberStatementsForExpression);
  }

  private static @Nullable PsiSwitchBlock generateEnhancedSwitch(@NotNull PsiStatement statementToReplace,
                                                                 List<SwitchBranch> newBranches,
                                                                 CommentTracker ct,
                                                                 boolean isExpr) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(statementToReplace.getProject());
    if (!(statementToReplace instanceof PsiSwitchStatement)) return null;
    PsiCodeBlock body = ((PsiSwitchStatement)statementToReplace).getBody();
    if (body == null) return null;

    StringBuilder sb = new StringBuilder();
    for (PsiElement e = statementToReplace.getFirstChild(); e != null && e != body; e = e.getNextSibling()) {
      sb.append(ct.text(e));
    }
    PsiJavaToken lBrace = body.getLBrace();
    sb.append(lBrace != null ? ct.textWithComments(lBrace) : "{");
    for (SwitchBranch newBranch : newBranches) {
      sb.append(newBranch.generate(ct));
    }
    PsiJavaToken rBrace = body.getRBrace();
    sb.append(rBrace != null ? ct.textWithComments(rBrace) : "}");
    PsiSwitchBlock switchBlock;
    if (isExpr) {
      switchBlock = (PsiSwitchBlock)factory.createExpressionFromText(sb.toString(), statementToReplace);
    }
    else {
      switchBlock = (PsiSwitchBlock)factory.createStatementFromText(sb.toString(), statementToReplace);
    }
    StreamEx.ofTree((PsiElement)switchBlock, block -> Arrays.stream(block.getChildren()))
      .select(PsiBreakStatement.class)
      .filter(breakStmt -> ControlFlowUtils.statementCompletesWithStatement(switchBlock, breakStmt) && breakStmt.findExitedStatement() == switchBlock)
      .forEach(statement -> new CommentTracker().delete(statement));
    return switchBlock;
  }

  private static boolean isExhaustiveSwitch(List<OldSwitchStatementBranch> branches, PsiSwitchStatement switchStatement) {
    for (OldSwitchStatementBranch branch : branches) {
      if (branch.isDefault()) return true;
      if (existsDefaultLabelElement(branch.myLabelStatement)) return true;
    }
    CompletenessResult completenessResult = evaluateSwitchCompleteness(switchStatement, true);
    return completenessResult == CompletenessResult.COMPLETE_WITHOUT_UNCONDITIONAL || completenessResult == CompletenessResult.COMPLETE_WITH_UNCONDITIONAL;
  }

  private static boolean isConvertibleBranch(@NotNull OldSwitchStatementBranch branch, boolean hasNext) {
    int length = branch.getStatements().length;
    if (length == 0) return (branch.isFallthrough() && hasNext) || (!branch.isFallthrough() && branch.isDefault());
    return !branch.isFallthrough();
  }

  private enum ReplacementType {
    Expression("inspection.replace.with.switch.expression.fix.name"),
    Statement("inspection.replace.with.enhanced.switch.statement.fix.name");
    private final @PropertyKey(resourceBundle = JavaBundle.BUNDLE) String key;

    ReplacementType(@PropertyKey(resourceBundle = JavaBundle.BUNDLE) String key) {
      this.key = key;
    }

    @Nls String getFixName() {
      return JavaBundle.message(key);
    }
  }

  public interface SwitchReplacer {
    void replace(@NotNull PsiStatement switchStatement);

    ReplacementType getType();

    boolean isInformLevel();

    //if null, it is not applicable
    @Nullable
    Integer getMaxNumberStatementsInBranch();
  }

  private interface SwitchConversion {
    @Nullable
    SwitchReplacer suggestReplacer(@NotNull PsiStatement statement,
                                   @NotNull List<OldSwitchStatementBranch> branches,
                                   boolean isExhaustive,
                                   int maxNumberStatementsForExpression);
  }

  //Right part of switch rule (case labels -> result)
  private interface SwitchRuleResult {
    String generate(CommentTracker ct, SwitchBranch branch);
  }

  private static class ReplaceWithSwitchExpressionFix extends PsiUpdateModCommandQuickFix {
    private final ReplacementType myReplacementType;

    ReplaceWithSwitchExpressionFix(ReplacementType replacementType) { myReplacementType = replacementType; }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.replace.with.switch.expression.fix.family.name");
    }

    @Override
    public @NotNull String getName() {
      return myReplacementType.getFixName();
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiSwitchStatement statement = PsiTreeUtil.getParentOfType(element, PsiSwitchStatement.class);
      if (statement == null) return;
      SwitchReplacer replacer =
        ContainerUtil.find(findSwitchReplacers(statement, Integer.MAX_VALUE), t -> t.getType() == myReplacementType);
      if (replacer == null) return;
      replacer.replace(statement);
    }
  }

  /**
   * This method is used to rearrange branches.
   * Now it can change the order of branches or divide some branches into several separate branches.
   * For example, a null branch will be extracted from others.
   * @return rearranged branches
   */
  private static @NotNull List<SwitchBranch> rearrangeBranches(@NotNull List<SwitchBranch> branches,
                                                      @NotNull PsiElement context) {
    if (branches.isEmpty()) {
      return branches;
    }
    if (!PsiUtil.isAvailable(JavaFeature.PATTERNS_IN_SWITCH, context)) {
      return branches;
    }
    List<SwitchBranch> result = new ArrayList<>();
    for (SwitchBranch branch : branches) {
      if (branch.myIsDefault) {
        result.add(branch);
        continue;
      }
      List<? extends PsiCaseLabelElement> caseExpressions = branch.myCaseExpressions;
      if (caseExpressions == null || caseExpressions.size() <= 1) {
        result.add(branch);
        continue;
      }
      rearrangeCases(branch, caseExpressions, result);
    }
    return result;
  }

  /**
   * This method is used to rearrange case elements.
   * Method is used by {@link #rearrangeBranches(List, PsiElement)}.
   * @param result - container, where sorted cases will be added
   */
  private static void rearrangeCases(@NotNull SwitchBranch branch,
                                     @NotNull List<? extends PsiCaseLabelElement> caseExpressions,
                                     @NotNull List<SwitchBranch> result) {
    List<PsiCaseLabelElement> previousExpressions = new ArrayList<>();
    for (PsiCaseLabelElement expression : caseExpressions) {
      PsiCaseLabelElement external = findNullLabel(List.of(expression));
      if (external == null && expression instanceof PsiPattern) {
        external = expression;
      }
      if (external == null) {
        previousExpressions.add(expression);
      }
      else {
        if (!previousExpressions.isEmpty()) {
          SwitchBranch otherBranch = branch.withLabels(new ArrayList<>(previousExpressions));
          result.add(otherBranch);
          previousExpressions.clear();
        }
        SwitchBranch specialBranch = branch.withLabels(List.of(external));
        result.add(specialBranch);
      }
    }
    if (!previousExpressions.isEmpty()) {
      SwitchBranch otherBranch = branch.withLabels(previousExpressions);
      result.add(otherBranch);
    }
  }

  private static @Nullable PsiCaseLabelElement findNullLabel(@NotNull List<? extends PsiCaseLabelElement> expressions) {
    return ContainerUtil.find(expressions, label -> label instanceof PsiExpression literal && TypeConversionUtil.isNullType(literal.getType()));
  }

  private static final class ReturningSwitchReplacer implements SwitchReplacer {
    final @NotNull PsiStatement myStatement;
    final List<SwitchBranch> myNewBranches;
    final @Nullable PsiReturnStatement myReturnToDelete;
    final @Nullable PsiThrowStatement myThrowStatementToDelete;
    private final @NotNull List<? extends PsiStatement> myStatementsToDelete;
    private final boolean myIsInfo;
    private final int myMaxNumberStatementsInBranch;

    private ReturningSwitchReplacer(@NotNull PsiStatement statement,
                                    @NotNull List<SwitchBranch> newBranches,
                                    @Nullable PsiReturnStatement returnToDelete,
                                    @Nullable PsiThrowStatement throwToDelete,
                                    @NotNull List<? extends PsiStatement> statementsToDelete,
                                    boolean isInfo,
                                    int maxNumberStatementsInBranch) {
      myStatement = statement;
      myNewBranches = rearrangeBranches(newBranches, statement);
      myReturnToDelete = returnToDelete;
      myThrowStatementToDelete = throwToDelete;
      myStatementsToDelete = statementsToDelete;
      myIsInfo = isInfo;
      myMaxNumberStatementsInBranch = maxNumberStatementsInBranch;
    }

    @Override
    public Integer getMaxNumberStatementsInBranch() {
      return myMaxNumberStatementsInBranch;
    }

    @Override
    public boolean isInformLevel() {
      return myIsInfo;
    }

    @Override
    public void replace(@NotNull PsiStatement statement) {
      CommentTracker commentTracker = new CommentTracker();
      PsiSwitchBlock switchBlock = generateEnhancedSwitch(statement, myNewBranches, commentTracker, true);
      if (switchBlock == null) return;

      if (myReturnToDelete != null) {
        CommentTracker ct = new CommentTracker();
        commentTracker.markUnchanged(myReturnToDelete.getReturnValue());
        ct.delete(myReturnToDelete);
      }
      if (myThrowStatementToDelete != null) {
        CommentTracker ct = new CommentTracker();
        commentTracker.markUnchanged(myThrowStatementToDelete.getException());
        ct.delete(myThrowStatementToDelete);
      }
      for (PsiStatement toDelete : myStatementsToDelete) {
        commentTracker.delete(toDelete);
      }
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(statement.getProject());
      PsiStatement returnStatement = factory.createStatementFromText("return " + switchBlock.getText() + ";", switchBlock);
      commentTracker.replaceAndRestoreComments(statement, returnStatement);
    }

    @Override
    public ReplacementType getType() {
      return ReplacementType.Expression;
    }
  }

  /**
   * <pre>
   * switch (n) {
   *   case 1:
   *     return "a";
   *   case 2:
   *     return "b";
   *   default:
   *     return "?";
   * }
   * </pre>
   */

  private static @Nullable SwitchReplacer inspectReturningSwitch(@NotNull PsiStatement statement,
                                                       @NotNull List<OldSwitchStatementBranch> branches,
                                                       boolean isExhaustive, int maxNumberStatementsForExpression) {
    PsiReturnStatement returnAfterSwitch =
      tryCast(PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class), PsiReturnStatement.class);
    PsiThrowStatement throwAfterSwitch =
      tryCast(PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class), PsiThrowStatement.class);
    if (returnAfterSwitch == null && throwAfterSwitch==null && !isExhaustive) return null;
    List<SwitchBranch> newBranches = new ArrayList<>();
    boolean hasReturningBranch = false;
    boolean isInfo = false;
    int maxLines = 0;
    for (int i = 0, size = branches.size(); i < size; i++) {
      OldSwitchStatementBranch branch = branches.get(i);
      if (!isConvertibleBranch(branch, i != size - 1)) return null;
      if (branch.isFallthrough()) continue;
      PsiStatement[] statements = branch.getStatements();
      if (statements.length == 1 && statements[0] instanceof PsiBlockStatement psiCodeBlock) {
        statements = psiCodeBlock.getCodeBlock().getStatements();
      }
      if (statements.length == 0) {
        if ((i == branches.size() - 1) || branch.isDefault()) {
          if (returnAfterSwitch != null) {
            statements = new PsiStatement[]{returnAfterSwitch};
          }
          else if (throwAfterSwitch != null) {
            statements = new PsiStatement[]{throwAfterSwitch};
          }
          else {
            return null;
          }
        }
        else {
          return null;
        }
      }
      if (maxLines < statements.length) {
        maxLines = statements.length;
      }
      if (statements.length > maxNumberStatementsForExpression) {
        isInfo = true;
      }
      int lastIndex = statements.length - 1;
      if (ContainerUtil.exists(statements,
                               st -> !PsiTreeUtil.findChildrenOfAnyType(st, PsiContinueStatement.class, PsiBreakStatement.class, PsiYieldStatement.class).isEmpty())) {
        return null;
      }
      PsiReturnStatement returnStmt = tryCast(statements[lastIndex], PsiReturnStatement.class);
      SwitchRuleResult result;
      if (returnStmt == null) {
        PsiThrowStatement throwStatement = tryCast(statements[lastIndex], PsiThrowStatement.class);
        if (throwStatement == null) return null;
        PsiStatement[] psiStatements = replaceAllReturnWithYield(statements);
        if (psiStatements == null) {
          return null;
        }
        result = new SwitchStatementBranch(psiStatements, statements);
      }
      else {
        PsiExpression returnExpr = returnStmt.getReturnValue();
        if (returnExpr == null) return null;
        if (statements.length == 1) {
          result = new SwitchRuleExpressionResult(returnExpr);
        }
        else {
          PsiStatement[] psiStatements = replaceAllReturnWithYield(statements);
          if (psiStatements == null) {
            return null;
          }
          result = new SwitchStatementBranch(psiStatements, statements);
        }
        hasReturningBranch = true;
      }
      newBranches.add(SwitchBranch.fromOldBranch(branch, result, branch.getUsedElements()));
    }
    if (!hasReturningBranch) return null;
    if (!isExhaustive && returnAfterSwitch != null) {
      PsiExpression returnExpr = returnAfterSwitch.getReturnValue();
      if (returnExpr == null) return null;
      newBranches.add(SwitchBranch.createDefault(new SwitchRuleExpressionResult(returnExpr)));
    }
    if (!isExhaustive && throwAfterSwitch != null) {
      newBranches.add(SwitchBranch.createDefault(new SwitchStatementBranch(new PsiStatement[]{throwAfterSwitch})));
    }
    List<PsiStatement> statementsToDelete = new ArrayList<>();
    if (isExhaustive && returnAfterSwitch == null && throwAfterSwitch == null) {
      PsiElement current = statement.getNextSibling();
      while (current != null) {
        if (current instanceof PsiStatement stmt) {
          if (current instanceof PsiSwitchLabelStatement) {
            break;
          }
          statementsToDelete.add(stmt);
          if (stmt instanceof PsiReturnStatement || stmt instanceof PsiThrowStatement) {
            break;
          }
        }
        current = current.getNextSibling();
      }
    }
    return new ReturningSwitchReplacer(statement, newBranches, returnAfterSwitch, throwAfterSwitch, statementsToDelete, isInfo, maxLines);
  }

  private static PsiStatement @Nullable [] replaceAllReturnWithYield(PsiStatement[] statements) {
    PsiStatement[] result = ArrayUtil.copyOf(statements);
    for (int i = 0; i < statements.length; i++) {
      PsiStatement statement = result[i];
      if (statement instanceof PsiReturnStatement returnStatement) {
        PsiExpression returnValue = returnStatement.getReturnValue();
        if (returnValue == null || PsiTreeUtil.hasErrorElements(returnValue) || returnValue.getType() == null) {
          return null;
        }
        result[i] = createYieldStatement(returnValue);
        continue;
      }
      PsiStatement copy = (PsiStatement)statement.copy();
      result[i] = copy;
      Collection<PsiReturnStatement> returnStatements = PsiTreeUtil.findChildrenOfType(copy, PsiReturnStatement.class);
      for (PsiReturnStatement returnStatement : returnStatements) {
        PsiExpression returnValue = returnStatement.getReturnValue();
        if (returnValue == null || PsiTreeUtil.hasErrorElements(returnValue) || returnValue.getType() == null) {
          return null;
        }
        returnStatement.replace(createYieldStatement(returnValue));
      }
    }
    return result;
  }

  private static PsiStatement[] withLastStatementReplacedWithYield(PsiStatement[] statements, @NotNull PsiExpression expr) {
    PsiStatement[] result = ArrayUtil.copyOf(statements);
    PsiStatement yieldStatement = createYieldStatement(expr);
    result[result.length - 1] = yieldStatement;
    return result;
  }

  private static @NotNull PsiStatement createYieldStatement(@NotNull PsiExpression expr) {
    Project project = expr.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    return factory.createStatementFromText("yield " + StringUtil.trim(expr.getText())  + ";", expr);
  }

  private static final class SwitchExistingVariableReplacer implements SwitchReplacer {
    final @NotNull PsiVariable myVariableToAssign;
    final @NotNull PsiStatement myStatement;
    final List<SwitchBranch> myNewBranches;
    final boolean myIsRightAfterDeclaration;
    private final boolean myIsInfo;
    private final int myMaxNumberStatementsInBranch;

    private SwitchExistingVariableReplacer(@NotNull PsiVariable variableToAssign,
                                           @NotNull PsiStatement statement,
                                           List<SwitchBranch> newBranches,
                                           boolean isRightAfterDeclaration,
                                           boolean isInfo,
                                           int maxNumberStatementsInBranch) {
      myVariableToAssign = variableToAssign;
      myStatement = statement;
      myNewBranches = rearrangeBranches(newBranches, statement);
      myIsRightAfterDeclaration = isRightAfterDeclaration;
      myIsInfo = isInfo;
      myMaxNumberStatementsInBranch = maxNumberStatementsInBranch;
    }

    @Override
    public Integer getMaxNumberStatementsInBranch() {
      return myMaxNumberStatementsInBranch;
    }

    @Override
    public boolean isInformLevel() {
      return myIsInfo;
    }

    @Override
    public void replace(@NotNull PsiStatement switchStatement) {
      PsiLabeledStatement labeledStatement = tryCast(switchStatement.getParent(), PsiLabeledStatement.class);
      CommentTracker commentTracker = new CommentTracker();
      PsiSwitchBlock replacement = generateEnhancedSwitch(switchStatement, myNewBranches, commentTracker, true);
      if (replacement == null) return;
      PsiExpression initializer = myVariableToAssign.getInitializer();
      if (myIsRightAfterDeclaration && isNotUsed(myVariableToAssign, switchStatement)) {
        if (initializer != null) {
          List<PsiExpression> sideEffectExpressions = SideEffectChecker.extractSideEffectExpressions(initializer);
          PsiStatement[] sideEffectStatements = StatementExtractor.generateStatements(sideEffectExpressions, initializer);
          if (sideEffectStatements.length > 0) {
            PsiStatement statement = tryCast(myVariableToAssign.getParent(), PsiStatement.class);
            if (statement == null) return;
            BlockUtils.addBefore(statement, sideEffectStatements);
          }
        }
        myVariableToAssign.setInitializer((PsiSwitchExpression)replacement);
        commentTracker.delete(switchStatement);
        commentTracker.insertCommentsBefore(myVariableToAssign);
        if (labeledStatement != null) {
          new CommentTracker().deleteAndRestoreComments(labeledStatement);
        }
      }
      else {
        String text = myVariableToAssign.getName() + "=" + replacement.getText() + ";";
        PsiStatement statementToReplace = labeledStatement != null ? labeledStatement : switchStatement;
        commentTracker.replaceAndRestoreComments(statementToReplace, text);
      }
    }

    private static boolean isNotUsed(@NotNull PsiVariable variable, @NotNull PsiStatement switchStatement) {
      try {
        ControlFlow controlFlow = ControlFlowFactory
          .getControlFlow(switchStatement, AllVariablesControlFlowPolicy.getInstance(), ControlFlowOptions.NO_CONST_EVALUATE);
        List<PsiReferenceExpression> references = ControlFlowUtil.getReadBeforeWrite(controlFlow);
        for (PsiReferenceExpression reference : references) {
          if (reference != null && reference.resolve() == variable) {
            return false;
          }
        }
        return true;
      }
      catch (AnalysisCanceledException e) {
        return false;
      }
    }

    @Override
    public ReplacementType getType() {
      return ReplacementType.Expression;
    }
  }

  /**
   * <pre>
   * int result;
   * switch(s) {
   *   case "a": result = 1; break;
   *   case "b": result = 2; break;
   *   default: result = 0;
   * }
   * </pre>
   */
  private static @Nullable SwitchReplacer inspectVariableAssigningSwitch(@NotNull PsiStatement statement,
                                                               @NotNull List<OldSwitchStatementBranch> branches,
                                                               boolean isExhaustive, int maxNumberStatementsForExpression) {
    PsiElement parent = statement.getParent();
    PsiElement anchor = parent instanceof PsiLabeledStatement ? parent : statement;
    PsiLocalVariable assignedVariable = null;
    List<SwitchBranch> newBranches = new ArrayList<>();
    boolean hasAssignedBranch = false;
    boolean wasDefault = false;
    boolean isInfo = false;
    int maxNumberStatementsInBranch = 0;
    for (int i = 0, size = branches.size(); i < size; i++) {
      OldSwitchStatementBranch branch = branches.get(i);
      if (!isConvertibleBranch(branch, i != size - 1)) return null;
      PsiStatement[] statements = branch.getStatements();
      if (branch.isFallthrough() && statements.length == 0) continue;
      if (statements.length == 0) return null;
      if (ContainerUtil.exists(statements,
                               st -> !PsiTreeUtil.findChildrenOfAnyType(st, PsiContinueStatement.class,
                                                                        PsiYieldStatement.class, PsiReturnStatement.class).isEmpty())) {
        return null;
      }
      if (ContainerUtil.exists(Arrays.stream(statements).toList().subList(0, statements.length - 1),
                               st -> !PsiTreeUtil.findChildrenOfAnyType(st, PsiBreakStatement.class).isEmpty())) {
        return null;
      }
      if (statements.length > maxNumberStatementsForExpression) {
        isInfo = true;
      }
      if (maxNumberStatementsInBranch < statements.length) {
        maxNumberStatementsInBranch = statements.length;
      }
      PsiStatement last = statements[statements.length - 1];
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(last);
      PsiExpression rExpression = null;
      if (assignment != null) {
        rExpression = assignment.getRExpression();
        PsiLocalVariable var = ExpressionUtils.resolveLocalVariable(assignment.getLExpression());
        if (var == null) return null;
        if (assignedVariable == null) {
          assignedVariable = var;
        }
        else if (assignedVariable != var) {
          return null;
        }
      }
      SwitchRuleResult result;
      if (rExpression == null) {
        PsiThrowStatement throwStatement = tryCast(last, PsiThrowStatement.class);
        if (throwStatement == null) return null;
        result = new SwitchStatementBranch(statements);
      }
      else {
        hasAssignedBranch = true;
        if (statements.length == 1) {
          result = new SwitchRuleExpressionResult(rExpression);
        }
        else {
          if (PsiTreeUtil.hasErrorElements(rExpression) || rExpression.getType() == null) {
            return null;
          }
          result = new SwitchStatementBranch(withLastStatementReplacedWithYield(statements, rExpression));
        }
      }
      if (branch.isDefault()) {
        wasDefault = true;
      }
      else {
        wasDefault = existsDefaultLabelElement(branch.myLabelStatement);
      }
      newBranches.add(SwitchBranch.fromOldBranch(branch, result, branch.getRelatedStatements()));
    }
    if (assignedVariable == null || !hasAssignedBranch) return null;
    boolean isRightAfterDeclaration = isRightAfterDeclaration(anchor, assignedVariable);
    if (!wasDefault && !isExhaustive) {
      SwitchBranch defaultBranch = getVariableAssigningDefaultBranch(assignedVariable, isRightAfterDeclaration, statement);
      if (defaultBranch != null) {
        newBranches.add(defaultBranch);
      }
      else {
        return null;
      }
    }
    return new SwitchExistingVariableReplacer(assignedVariable, statement, newBranches, isRightAfterDeclaration, isInfo, maxNumberStatementsInBranch);
  }

  private static boolean existsDefaultLabelElement(@NotNull PsiSwitchLabelStatement statement) {
    PsiCaseLabelElementList labelElementList = statement.getCaseLabelElementList();
    if (labelElementList == null) return false;
    return ContainerUtil.exists(labelElementList.getElements(), el -> el instanceof PsiDefaultCaseLabelElement);
  }

  private static @Nullable EnhancedSwitchMigrationInspection.SwitchBranch getVariableAssigningDefaultBranch(@Nullable PsiLocalVariable assignedVariable,
                                                                                                            boolean isRightAfterDeclaration,
                                                                                                            @NotNull PsiStatement statement) {
    if (assignedVariable == null) return null;
    PsiExpression initializer = assignedVariable.getInitializer();
    if (isRightAfterDeclaration && initializer instanceof PsiLiteralExpression) {
      return SwitchBranch.createDefault(new SwitchRuleExpressionResult(initializer));
    }
    PsiDeclarationStatement declaration = tryCast(assignedVariable.getParent(), PsiDeclarationStatement.class);
    if (declaration == null || declaration.getParent()==null) return null;
    try {
      final LocalsOrMyInstanceFieldsControlFlowPolicy policy = LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance();
      final ControlFlow controlFlow = ControlFlowFactory.getInstance(declaration.getProject()).getControlFlow(declaration.getParent(), policy);
      final int switchStart = controlFlow.getStartOffset(statement);
      if (switchStart <= 0) {
        return null;
      }
      final ControlFlow beforeFlow = new ControlFlowSubRange(controlFlow, 0, switchStart);
      if (!ControlFlowUtil.isVariableDefinitelyAssigned(assignedVariable, beforeFlow)) {
        return null;
      }
    }
    catch (AnalysisCanceledException e) {
      return null;
    }
    Project project = assignedVariable.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiExpression reference = factory.createExpressionFromText(assignedVariable.getName(), assignedVariable);
    return SwitchBranch.createDefault(new SwitchRuleExpressionResult(reference));
  }

  private static boolean isRightAfterDeclaration(PsiElement anchor, PsiVariable assignedVariable) {
    PsiDeclarationStatement declaration = tryCast(PsiTreeUtil.getPrevSiblingOfType(anchor, PsiStatement.class), PsiDeclarationStatement.class);
    if (declaration != null) {
      PsiElement[] elements = declaration.getDeclaredElements();
      if (elements.length == 1) {
        PsiLocalVariable localVariable = tryCast(elements[0], PsiLocalVariable.class);
        if (localVariable != null && localVariable == assignedVariable) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Replaces with an enhanced switch statement
   */
  private static final class SwitchStatementReplacer implements SwitchReplacer {
    final @NotNull PsiStatement myStatement;
    final @NotNull List<SwitchBranch> myExpressionBranches;

    private SwitchStatementReplacer(@NotNull PsiStatement statement,
                                    @NotNull List<SwitchBranch> ruleResults) {
      myStatement = statement;
      myExpressionBranches = rearrangeBranches(ruleResults, statement);
    }

    @Override
    public boolean isInformLevel() {
      return false;
    }

    @Override
    public Integer getMaxNumberStatementsInBranch() {
      return null;
    }

    @Override
    public void replace(@NotNull PsiStatement switchStatement) {
      CommentTracker commentTracker = new CommentTracker();
      PsiSwitchBlock switchBlock = generateEnhancedSwitch(switchStatement, myExpressionBranches, commentTracker, false);
      if (switchBlock == null) return;
      commentTracker.replaceAndRestoreComments(switchStatement, switchBlock);
    }

    @Override
    public ReplacementType getType() {
      return ReplacementType.Statement;
    }
  }

  /**
   * Suggest replacement with an enhanced switch statement
   */
  private static @Nullable SwitchReplacer inspectReplacementWithStatement(@NotNull PsiStatement statement,
                                                                @NotNull List<OldSwitchStatementBranch> branches) {
    for (int i = 0, size = branches.size(); i < size; i++) {
      OldSwitchStatementBranch branch = branches.get(i);
      if (!isConvertibleBranch(branch, i != size - 1) &&
          //example:
          //case 0: break
          !(!branch.isFallthrough() && branch.getStatements().length == 0)) {
        return null;
      }
    }
    List<SwitchBranch> switchRules = new ArrayList<>();
    for (int i = 0, branchesSize = branches.size(); i < branchesSize; i++) {
      OldSwitchStatementBranch branch = branches.get(i);
      if (branch.isFallthrough() && branch.getStatements().length == 0) continue;
      boolean allBranchRefsWillBeValid = StreamEx.of(branch.getStatements())
        .limit(i) // only previous branches
        .flatMap((PsiElement stmt) -> StreamEx.ofTree(stmt, el -> StreamEx.of(el.getChildren())))
        .select(PsiReferenceExpression.class)
        .map(PsiReference::resolve)
        .select(PsiLocalVariable.class)
        .allMatch(variable -> isInBranchOrOutside(statement, branch, variable));
      if (!allBranchRefsWillBeValid) return null;
      if (branch.isFallthrough() && branch.getStatements().length == 0) continue;
      PsiStatement[] statements = branch.getStatements();
      switchRules.add(SwitchBranch.fromOldBranch(branch, new SwitchStatementBranch(statements), branch.getRelatedStatements()));
    }
    return new SwitchStatementReplacer(statement, switchRules);
  }


  private static boolean isInBranchOrOutside(@NotNull PsiStatement switchStmt,
                                             OldSwitchStatementBranch branch, PsiLocalVariable variable) {
    return !PsiTreeUtil.isAncestor(switchStmt, variable, false)
           || ContainerUtil.or(branch.getStatements(), stmt -> PsiTreeUtil.isAncestor(stmt, variable, false));
  }

  private static final class SwitchStatementBranch implements SwitchRuleResult {

    private final @Nullable PsiStatement @Nullable [] myResultStatements;

    private final @Nullable PsiStatement @Nullable [] myOriginalResultStatements;

    private SwitchStatementBranch(@Nullable PsiStatement @Nullable[] resultStatements) {
      myResultStatements = resultStatements;
      myOriginalResultStatements = null;
    }

    private SwitchStatementBranch(@Nullable PsiStatement @Nullable[] resultStatements, @Nullable PsiStatement @Nullable[] originalResultStatements) {
      myResultStatements = resultStatements;
      myOriginalResultStatements = originalResultStatements;
    }

    @Override
    public String generate(CommentTracker ct, SwitchBranch branch) {
      @Nullable PsiStatement @Nullable [] resultStatements = myResultStatements;
      if(resultStatements == null) return "";
      if (resultStatements.length == 1) {
        PsiStatement first = resultStatements[0];
        if (first instanceof PsiExpressionStatement || first instanceof PsiBlockStatement || first instanceof PsiThrowStatement) {
          return ct.textWithComments(resultStatements[0]) + "\n";
        }
      }
      StringBuilder sb = new StringBuilder("{");
      for (int i = 0, length = resultStatements.length; i < length; i++) {
        PsiStatement element = resultStatements[i];
        if (element == null) continue;
        if (i == 0) {
          PsiElement current = getElementForComments(element, i);
          if(current != null) {
            current = current.getPrevSibling();
          }
          while (current instanceof PsiWhiteSpace || current instanceof PsiComment) {
            current = current.getPrevSibling();
          }
          addWhiteSpaceAndComments(current, sb, ct);
        }
        sb.append(ct.text(element));
        if (i + 1 < length) {
          PsiElement current = getElementForComments(element, i);
          addWhiteSpaceAndComments(current, sb, ct);
        }
        if (element.getNextSibling() == null && element.getLastChild() instanceof PsiComment comment &&
            comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
          addNewLine(sb);
        }
      }
      addCommentsUntilNextLabel(ct, branch, sb);
      addNewLine(sb);
      sb.append("}");
      for (PsiElement element : branch.myUsedElements) {
        ct.markUnchanged(element);
      }
      return sb.toString();
    }

    private static void addNewLine(@NotNull StringBuilder sb) {
      String string = sb.toString();
      String trimmed = string.trim();
      if(!string.substring(trimmed.length()).contains("\n")) {
        sb.append("\n");
      }
    }

    @Nullable
    private PsiElement getElementForComments(@Nullable PsiStatement element, int i) {
      PsiElement current = element;
      if (myOriginalResultStatements != null &&
          myOriginalResultStatements.length > i &&
          myOriginalResultStatements[i] != null) {
        current = myOriginalResultStatements[i];
      }
      return current;
    }

    private static void addWhiteSpaceAndComments(@Nullable PsiElement element, @NotNull StringBuilder sb, CommentTracker ct) {
      if (element == null) {
        return;
      }
      PsiElement current = element.getNextSibling();
      while (current instanceof PsiComment || current instanceof PsiWhiteSpace) {
        sb.append(ct.text(current));
        current = current.getNextSibling();
      }
    }
  }

  private static final class SwitchRuleExpressionResult implements SwitchRuleResult {
    private final PsiExpression myExpression;

    private SwitchRuleExpressionResult(@NotNull PsiExpression expression) { myExpression = expression; }

    @Override
    public String generate(CommentTracker ct, SwitchBranch branch) {
      return ct.textWithComments(myExpression) + ";";
    }
  }

  /**
   * Adds comments until the next label statement in a switch branch.
   * If comments exist, <code>builder</code> will end with '\n'
   *
   * @param ct      the CommentTracker object for tracking comments
   * @param branch  the SwitchBranch object representing a switch branch
   * @param builder the StringBuilder object to append comments to
   */
  private static void addCommentsUntilNextLabel(CommentTracker ct, SwitchBranch branch, StringBuilder builder) {
    PsiElement label = ContainerUtil.find(branch.myUsedElements, e -> e instanceof PsiSwitchLabelStatement);
    if (!(label instanceof PsiSwitchLabelStatement labelStatement)) {
      return;
    }
    PsiSwitchLabelStatement nextLabelStatement = PsiTreeUtil.getNextSiblingOfType(labelStatement, PsiSwitchLabelStatement.class);
    PsiElement untilComment = null;
    if (nextLabelStatement != null) {
      untilComment = PsiTreeUtil.getPrevSiblingOfType(nextLabelStatement, PsiStatement.class);
    }
    if (untilComment == null) {
      PsiElement next = labelStatement.getNextSibling();
      if (next != null) {
        while (next.getNextSibling() != null) {
          next = next.getNextSibling();
        }
      }
      untilComment = next;
    }
    if (untilComment != null) {
      String commentsBefore = grubCommentsBefore(untilComment, ct, branch).stripTrailing();
      String previousText = builder.toString().stripTrailing();
      if (previousText.length() > 1 && previousText.charAt(builder.length() - 1) == '\n') {
        commentsBefore = StringUtil.trimStart(commentsBefore, "\n");
      }
      if (!commentsBefore.isEmpty()) {
        commentsBefore += '\n';
      }
      builder.append(commentsBefore);
    }
  }

  @NotNull
  private static String grubCommentsBefore(@NotNull PsiElement untilComment, @NotNull CommentTracker ct, SwitchBranch branch) {
    List<String> comments = new ArrayList<>();
    PsiElement current =
      (untilComment instanceof PsiComment || untilComment instanceof PsiWhiteSpace) ? untilComment : PsiTreeUtil.prevLeaf(untilComment);
    while (current != null) {
      if ((current instanceof PsiComment || current instanceof PsiWhiteSpace)) {
        if (branch.myUsedElements.isEmpty() ||
            !PsiTreeUtil.isAncestor(branch.myUsedElements.get(branch.myUsedElements.size() - 1), current, false)) {
          comments.add(ct.text(current));
        }
      }
      else {
        break;
      }
      current = PsiTreeUtil.prevLeaf(current);
    }
    Collections.reverse(comments);
    return StringUtil.join(comments, "");
  }

  private static final class SwitchBranch {
    final boolean myIsDefault;
    final List<? extends PsiCaseLabelElement> myCaseExpressions;
    final @Nullable PsiExpression myGuard;
    final @NotNull List<? extends PsiElement> myUsedElements; // used elements only for this branch
    private final @NotNull SwitchRuleResult myRuleResult;

    private SwitchBranch(boolean isDefault,
                         @NotNull List<? extends PsiCaseLabelElement> caseExpressions,
                         @Nullable PsiExpression guard, @NotNull SwitchRuleResult ruleResult,
                         @NotNull List<? extends PsiElement> usedElements) {
      if (ContainerUtil.exists(caseExpressions, exp -> exp instanceof PsiDefaultCaseLabelElement)) {
        myIsDefault = true;
      }
      else {
        myIsDefault = isDefault;
      }
      myGuard = guard;
      if (myIsDefault) {
        PsiCaseLabelElement nullLabel = findNullLabel(caseExpressions);
        if (nullLabel != null) {
          myCaseExpressions = List.of(nullLabel);
        }
        else {
          myCaseExpressions = List.of();
        }
      }
      else {
        myCaseExpressions = caseExpressions;
      }
      myRuleResult = ruleResult;
      myUsedElements = usedElements;
    }

    private String generate(CommentTracker ct) {
      StringBuilder sb = new StringBuilder();
      PsiElement label = ContainerUtil.find(myUsedElements, e -> e instanceof PsiSwitchLabelStatement);
      if (label != null) {
        sb.append(ct.commentsBefore(label.getFirstChild()));
      }
      if (!myCaseExpressions.isEmpty()) {
        String labels = StreamEx.of(myCaseExpressions).map(ct::textWithComments).joining(",");
        sb.append("case");
        if (!labels.startsWith(" ")) {
          sb.append(" ");
        }
        sb.append(labels);
      }
      else if (!myIsDefault) {
        sb.append("case ");
      }
      if (myIsDefault) {
        if (!myCaseExpressions.isEmpty()) {
          sb.append(",");
        }
        sb.append("default");
      }
      if (myGuard != null) {
        sb.append(" when ").append(ct.text(myGuard));
      }
      grabCommentsBeforeColon(label, ct, sb);
      sb.append("->");
      sb.append(myRuleResult.generate(ct, this));
      return sb.toString();
    }

    private static void grabCommentsBeforeColon(PsiElement label, CommentTracker ct, StringBuilder sb) {
      if (label != null) {
        PsiElement child = label.getLastChild();
        while (child != null && !child.textMatches(":")) {
          child = child.getPrevSibling();
        }
        if (child != null) {
          sb.append(ct.commentsBefore(child));
        }
      }
    }

    private @NotNull SwitchBranch withLabels(@NotNull List<PsiCaseLabelElement> caseElements) {
      return new SwitchBranch(myIsDefault, caseElements, myGuard, myRuleResult, myUsedElements);
    }

    private static @NotNull SwitchBranch createDefault(@NotNull SwitchRuleResult ruleResult) {
      return new SwitchBranch(true, Collections.emptyList(), null, ruleResult, Collections.emptyList());
    }

    private static @NotNull SwitchBranch fromOldBranch(@NotNull OldSwitchStatementBranch branch,
                                                       @NotNull SwitchRuleResult result,
                                                       @NotNull List<? extends PsiElement> usedElements) {
      return new SwitchBranch(branch.isDefault(), branch.getCaseLabelElements(), branch.getGuardExpression(), result, usedElements);
    }
  }

  private static final class OldSwitchStatementBranch {
    final boolean myIsFallthrough;
    final PsiStatement @NotNull [] myStatements;
    final @NotNull PsiSwitchLabelStatement myLabelStatement;
    final @Nullable PsiBreakStatement myBreakStatement;
    @Nullable OldSwitchStatementBranch myPreviousSwitchBranch;

    private OldSwitchStatementBranch(boolean isFallthrough,
                                     PsiStatement @NotNull [] statements,
                                     @NotNull PsiSwitchLabelStatement switchLabelStatement,
                                     @Nullable PsiBreakStatement breakStatement) {
      myIsFallthrough = isFallthrough;
      myStatements = statements;
      myLabelStatement = switchLabelStatement;
      myBreakStatement = breakStatement;
    }

    private boolean isDefault() {
      List<OldSwitchStatementBranch> branches = getWithFallthroughBranches();
      return ContainerUtil.or(branches, branch -> branch.myLabelStatement.isDefaultCase());
    }

    private boolean isFallthrough() {
      return myIsFallthrough;
    }

    public @Nullable PsiExpression getGuardExpression() {
      return myLabelStatement.getGuardExpression();
    }
    
    private List<PsiCaseLabelElement> getCaseLabelElements() {
      List<OldSwitchStatementBranch> branches = getWithFallthroughBranches();
      Collections.reverse(branches);
      return StreamEx.of(branches).flatMap(branch -> {
        final PsiCaseLabelElementList caseLabelElementList = branch.myLabelStatement.getCaseLabelElementList();
        if (caseLabelElementList == null) return StreamEx.empty();
        return StreamEx.of(caseLabelElementList.getElements());
      }).toList();
    }

    /**
     * @return only meaningful statements, without break and case statements
     */
    private PsiStatement[] getStatements() {
      return myStatements;
    }

    private List<PsiStatement> getRelatedStatements() {
      StreamEx<PsiStatement> withoutBreak = StreamEx.of(myStatements).prepend(myLabelStatement);
      return withoutBreak.prepend(myBreakStatement).toList();
    }

    private List<OldSwitchStatementBranch> getWithFallthroughBranches() {
      List<OldSwitchStatementBranch> withPrevious = new ArrayList<>();
      OldSwitchStatementBranch current = this;
      while (true) {
        withPrevious.add(current);
        current = current.myPreviousSwitchBranch;
        if (current == null || current.myStatements.length != 0 || !current.myIsFallthrough) {
          return withPrevious;
        }
      }
    }

    private List<? extends PsiElement> getUsedElements() {
      return StreamEx.of(getWithFallthroughBranches()).flatMap(branch -> StreamEx.of(branch.getRelatedStatements())).toList();
    }
  }
}

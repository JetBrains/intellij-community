// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.analysis.SwitchBlockHighlightingModel.PatternsInSwitchBlockHighlightingModel.CompletenessResult;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInsight.daemon.impl.analysis.SwitchBlockHighlightingModel.PatternsInSwitchBlockHighlightingModel;
import static com.intellij.util.ObjectUtils.tryCast;

public class EnhancedSwitchMigrationInspection extends AbstractBaseJavaLocalInspectionTool {
  public boolean myWarnOnlyOnExpressionConversion = false;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(JavaBundle.message("inspection.switch.expression.migration.warn.only.on.expression"),
                                          this,
                                          "myWarnOnlyOnExpressionConversion");
  }

  private final static SwitchConversion[] ourInspections = new SwitchConversion[]{
    EnhancedSwitchMigrationInspection::inspectReturningSwitch,
    EnhancedSwitchMigrationInspection::inspectVariableAssigningSwitch,
    EnhancedSwitchMigrationInspection::inspectReplacementWithStatement
  };

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.ENHANCED_SWITCH.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
        SwitchReplacer replacer = findSwitchReplacer(statement);
        if (replacer == null) return;
        PsiElement switchKeyword = statement.getFirstChild();
        ProblemHighlightType type = myWarnOnlyOnExpressionConversion && replacer.getType() == ReplacementType.Statement
                                    ? ProblemHighlightType.INFORMATION
                                    : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        if (!holder.isOnTheFly() && type == ProblemHighlightType.INFORMATION) return;
        List<LocalQuickFix> fixes = new ArrayList<>();
        fixes.add(new ReplaceWithSwitchExpressionFix(replacer.getType()));
        if (!myWarnOnlyOnExpressionConversion && replacer.getType() == ReplacementType.Statement) {
          fixes.add(new SetInspectionOptionFix(EnhancedSwitchMigrationInspection.this,
                                               "myWarnOnlyOnExpressionConversion",
                                               JavaBundle.message("inspection.switch.expression.migration.warn.only.on.expression"),
                                               true));
        }
        holder.registerProblem(switchKeyword, JavaBundle.message(
          "inspection.switch.expression.migration.inspection.switch.description"), type, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
      }
    };
  }

  @Nullable
  private static SwitchReplacer runInspections(PsiStatement statement, boolean isExhaustive, List<OldSwitchStatementBranch> branches) {
    for (SwitchConversion inspection : ourInspections) {
      SwitchReplacer replacer = inspection.suggestReplacer(statement, branches, isExhaustive);
      if (replacer != null) return replacer;
    }
    return null;
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

  @Nullable
  private static List<OldSwitchStatementBranch> extractBranches(@NotNull PsiCodeBlock body,
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
   * Before using this method make sure you are using correct version of Java.
   *
   */
  @Nullable
  public static SwitchReplacer findSwitchReplacer(PsiSwitchStatement switchStatement) {
    PsiExpression expression = switchStatement.getExpression();
    if (expression == null) return null;
    PsiCodeBlock body = switchStatement.getBody();
    if (body == null) return null;
    List<OldSwitchStatementBranch> branches = extractBranches(body, switchStatement);
    if (branches == null || branches.isEmpty()) return null;
    boolean isExhaustive = isExhaustiveSwitch(branches, switchStatement);
    return runInspections(switchStatement, isExhaustive, branches);
  }

  @Nullable
  private static PsiSwitchBlock generateEnhancedSwitch(@NotNull PsiStatement statementToReplace,
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
    CompletenessResult completenessResult = PatternsInSwitchBlockHighlightingModel.evaluateSwitchCompleteness(switchStatement);
    return completenessResult == CompletenessResult.COMPLETE_WITHOUT_TOTAL || completenessResult == CompletenessResult.COMPLETE_WITH_TOTAL;
  }

  private static boolean isConvertibleBranch(OldSwitchStatementBranch branch, boolean allowMultipleStatements, boolean hasNext) {
    int length = branch.getStatements().length;
    if (length == 0) return (branch.isFallthrough() && hasNext) || (!branch.isFallthrough() && branch.isDefault());
    if (branch.isFallthrough()) return false;
    if (allowMultipleStatements) return true;
    return length == 1;
  }

  private enum ReplacementType {
    Expression("inspection.replace.with.switch.expression.fix.name"),
    Statement("inspection.replace.with.enhanced.switch.statement.fix.name");
    @PropertyKey(resourceBundle = JavaBundle.BUNDLE)
    private final String key;

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
  }

  private interface SwitchConversion {
    @Nullable
    SwitchReplacer suggestReplacer(@NotNull PsiStatement statement,
                                   @NotNull List<OldSwitchStatementBranch> branches,
                                   boolean isExhaustive
    );
  }

  //Right part of switch rule (case labels -> result)
  private interface SwitchRuleResult {
    String generate(CommentTracker ct);
  }

  private static class ReplaceWithSwitchExpressionFix implements LocalQuickFix {
    private final ReplacementType myReplacementType;

    ReplaceWithSwitchExpressionFix(ReplacementType replacementType) {myReplacementType = replacementType;}

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.replace.with.switch.expression.fix.family.name");
    }

    @Override
    public @NotNull String getName() {
      return myReplacementType.getFixName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiSwitchStatement statement = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiSwitchStatement.class);
      if (statement == null) return;
      SwitchReplacer replacer = findSwitchReplacer(statement);
      if (replacer == null) return;
      replacer.replace(statement);
    }
  }

  private static final class ReturningSwitchReplacer implements SwitchReplacer {
    @NotNull final PsiStatement myStatement;
    final List<SwitchBranch> myNewBranches;
    final @Nullable PsiReturnStatement myReturnToDelete;
    private final List<PsiStatement> myStatementsToDelete;

    private ReturningSwitchReplacer(@NotNull PsiStatement statement,
                                    @NotNull List<SwitchBranch> newBranches,
                                    @Nullable PsiReturnStatement returnToDelete,
                                    @NotNull List<PsiStatement> statementsToDelete) {
      myStatement = statement;
      myNewBranches = newBranches;
      myReturnToDelete = returnToDelete;
      myStatementsToDelete = statementsToDelete;
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

  @Nullable
  private static SwitchReplacer inspectReturningSwitch(@NotNull PsiStatement statement,
                                                       @NotNull List<OldSwitchStatementBranch> branches,
                                                       boolean isExhaustive) {
    PsiReturnStatement returnAfterSwitch =
      tryCast(PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class), PsiReturnStatement.class);
    if (returnAfterSwitch == null && !isExhaustive) return null;
    List<SwitchBranch> newBranches = new ArrayList<>();
    boolean hasReturningBranch = false;
    boolean forceNextDefaultBranch = false;
    for (int i = 0, size = branches.size(); i < size; i++) {
      OldSwitchStatementBranch branch = branches.get(i);
      if (!isConvertibleBranch(branch, false, i != size - 1)) return null;
      if (branch.isFallthrough()) {
        if (branch.isDefault() && i < size - 1) {
          forceNextDefaultBranch = true;
        }
        continue;
      }
      PsiStatement[] statements = branch.getStatements();
      if (statements.length != 1) return null;
      PsiReturnStatement returnStmt = tryCast(statements[0], PsiReturnStatement.class);
      SwitchRuleResult result;
      if (returnStmt == null) {
        PsiThrowStatement throwStatement = tryCast(statements[0], PsiThrowStatement.class);
        if (throwStatement == null) return null;
        result = new SwitchStatementBranch(new PsiStatement[]{throwStatement});
      }
      else {
        PsiExpression returnExpr = returnStmt.getReturnValue();
        if (returnExpr == null) return null;
        result = new SwitchRuleExpressionResult(returnExpr);
        hasReturningBranch = true;
      }
      if (forceNextDefaultBranch) {
        newBranches.add(  new SwitchBranch(true,
                         Collections.emptyList(),
                         result,
                         branch.getUsedElements()));
      } else {
        newBranches.add(new SwitchBranch(branch.isDefault(),
                                         branch.getCaseLabelElements(),
                                         result,
                                         branch.getUsedElements()));
      }
      forceNextDefaultBranch = false;
    }
    if (!hasReturningBranch) return null;
    if (!isExhaustive) {
      PsiExpression returnExpr = returnAfterSwitch.getReturnValue();
      if (returnExpr == null) return null;
      newBranches.add(new SwitchBranch(true,
                                       Collections.emptyList(),
                                       new SwitchRuleExpressionResult(returnExpr),
                                       Collections.emptyList()));
    }
    List<PsiStatement> statementsToDelete = new ArrayList<>();
    if (isExhaustive && returnAfterSwitch == null) {
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
    return new ReturningSwitchReplacer(statement, newBranches, returnAfterSwitch, statementsToDelete);
  }

  private static final class SwitchExistingVariableReplacer implements SwitchReplacer {
    @NotNull final PsiVariable myVariableToAssign;
    @NotNull final PsiStatement myStatement;
    final List<SwitchBranch> myNewBranches;
    final boolean myIsRightAfterDeclaration;

    private SwitchExistingVariableReplacer(
      @NotNull PsiVariable variableToAssign,
      @NotNull PsiStatement statement,
      List<SwitchBranch> newBranches,
      boolean isRightAfterDeclaration) {
      myVariableToAssign = variableToAssign;
      myStatement = statement;
      myNewBranches = newBranches;
      myIsRightAfterDeclaration = isRightAfterDeclaration;
    }

    @Override
    public void replace(@NotNull PsiStatement switchStatement) {
      PsiLabeledStatement labeledStatement = tryCast(switchStatement.getParent(), PsiLabeledStatement.class);
      CommentTracker commentTracker = new CommentTracker();
      PsiSwitchBlock replacement = generateEnhancedSwitch(switchStatement, myNewBranches, commentTracker, true);
      if (replacement == null) return;
      PsiExpression initializer = myVariableToAssign.getInitializer();
      if (myIsRightAfterDeclaration) {
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
      } else {
        String text = myVariableToAssign.getName() + "=" + replacement.getText() + ";";
        PsiStatement statementToReplace = labeledStatement != null ? labeledStatement : switchStatement;
        commentTracker.replaceAndRestoreComments(statementToReplace, text);
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
  @Nullable
  private static SwitchReplacer inspectVariableAssigningSwitch(@NotNull PsiStatement statement,
                                                               @NotNull List<OldSwitchStatementBranch> branches,
                                                               boolean isExhaustive) {
    PsiElement parent = statement.getParent();
    PsiElement anchor = parent instanceof PsiLabeledStatement ? parent : statement;
    PsiLocalVariable assignedVariable = null;
    List<SwitchBranch> newBranches = new ArrayList<>();
    boolean hasAssignedBranch = false;
    boolean wasDefault = false;
    for (int i = 0, size = branches.size(); i < size; i++) {
      OldSwitchStatementBranch branch = branches.get(i);
      if (!isConvertibleBranch(branch, false, i != size - 1)) return null;
      PsiStatement[] statements = branch.getStatements();
      if (branch.isFallthrough() && statements.length == 0) continue;
      // Only single statement branches are convertible now
      PsiStatement first = statements.length > 0 ? statements[0] : null;
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(first);
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
        PsiThrowStatement throwStatement = tryCast(first, PsiThrowStatement.class);
        if (throwStatement == null) return null;
        result = new SwitchStatementBranch(new PsiStatement[]{throwStatement});
      }
      else {
        hasAssignedBranch = true;
        result = new SwitchRuleExpressionResult(rExpression);
      }
      boolean isDefault = branch.isDefault();
      if (isDefault) {
        wasDefault = true;
      }
      else {
        wasDefault = existsDefaultLabelElement(branch.myLabelStatement);
      }
      newBranches.add(new SwitchBranch(isDefault, branch.getCaseLabelElements(), result, branch.getRelatedStatements()));
    }
    if (assignedVariable == null || !hasAssignedBranch) return null;
    boolean isRightAfterDeclaration = isRightAfterDeclaration(anchor, assignedVariable);
    if (!wasDefault && !isExhaustive) {
      SwitchBranch defaultBranch = getVariableAssigningDefaultBranch(assignedVariable, isRightAfterDeclaration, statement);
      if (defaultBranch != null) {
        newBranches.add(defaultBranch);
      } else {
        return null;
      }
    }
    return new SwitchExistingVariableReplacer(assignedVariable, statement, newBranches, isRightAfterDeclaration);
  }

  private static boolean existsDefaultLabelElement(@NotNull PsiSwitchLabelStatement statement) {
    PsiCaseLabelElementList labelElementList = statement.getCaseLabelElementList();
    if (labelElementList == null) return false;
    return ContainerUtil.exists(labelElementList.getElements(), el -> el instanceof PsiDefaultCaseLabelElement);
  }

  @Nullable
  private static EnhancedSwitchMigrationInspection.SwitchBranch getVariableAssigningDefaultBranch(@Nullable PsiLocalVariable assignedVariable,
                                                                                                  boolean isRightAfterDeclaration,
                                                                                                  @NotNull PsiStatement statement) {
    if (assignedVariable == null) return null;
    PsiExpression initializer = assignedVariable.getInitializer();
    if (isRightAfterDeclaration && initializer != null) {
      return new SwitchBranch(true, Collections.emptyList(), new SwitchRuleExpressionResult(initializer), Collections.emptyList());
    }
    PsiDeclarationStatement declaration = tryCast(assignedVariable.getParent(), PsiDeclarationStatement.class);
    if (declaration == null) return null;
    if (!VariableAccessUtils.variableIsAssignedAtPoint(assignedVariable, declaration.getParent(), statement)) return null;
    Project project = assignedVariable.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiExpression reference = factory.createExpressionFromText(assignedVariable.getName(), assignedVariable);
    return new SwitchBranch(true, Collections.emptyList(), new SwitchRuleExpressionResult(reference), Collections.emptyList());
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
   * Replaces with enhanced switch statement
   */
  private static final class SwitchStatementReplacer implements SwitchReplacer {
    @NotNull final PsiStatement myStatement;
    @NotNull final List<SwitchBranch> myExpressionBranches;

    private SwitchStatementReplacer(@NotNull PsiStatement statement,
                                    @NotNull List<SwitchBranch> ruleResults) {
      myStatement = statement;
      myExpressionBranches = ruleResults;
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
   * Suggest replacement with enhanced switch statement
   */
  @Nullable
  private static SwitchReplacer inspectReplacementWithStatement(@NotNull PsiStatement statement,
                                                                @NotNull List<OldSwitchStatementBranch> branches,
                                                                boolean isExhaustive) {
    for (int i = 0, size = branches.size(); i < size; i++) {
      OldSwitchStatementBranch branch = branches.get(i);
      if (!isConvertibleBranch(branch, true, i != size - 1)) return null;
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
      switchRules.add(new SwitchBranch(branch.isDefault(), branch.getCaseLabelElements(),
                                       new SwitchStatementBranch(statements),
                                       branch.getRelatedStatements()));
    }
    return new SwitchStatementReplacer(statement, switchRules);
  }


  private static boolean isInBranchOrOutside(@NotNull PsiStatement switchStmt,
                                             OldSwitchStatementBranch branch, PsiLocalVariable variable) {
    return !PsiTreeUtil.isAncestor(switchStmt, variable, false)
           || ContainerUtil.or(branch.getStatements(), stmt -> PsiTreeUtil.isAncestor(stmt, variable, false));
  }

  private static final class SwitchStatementBranch implements SwitchRuleResult {
    final PsiStatement[] myResultStatements;

    private SwitchStatementBranch(PsiStatement[] resultStatements) {
      myResultStatements = resultStatements;
    }

    @Override
    public String generate(CommentTracker ct) {
      if (myResultStatements.length == 1) {
        PsiStatement first = myResultStatements[0];
        if (first instanceof PsiExpressionStatement || first instanceof PsiBlockStatement || first instanceof PsiThrowStatement) return ct.textWithComments(myResultStatements[0]) + "\n";
      }
      StringBuilder sb = new StringBuilder("{");
      for (int i = 0, length = myResultStatements.length; i < length; i++) {
        PsiStatement element = myResultStatements[i];
        String text = ct.textWithComments(element);
        if (i == length - 1) {
          sb.append(text);
          continue;
        }
        int lastCommentIndex = text.lastIndexOf("//");
        if (lastCommentIndex == -1) {
          sb.append(text);
          continue;
        }
        String afterComment = text.substring(lastCommentIndex);
        if (afterComment.contains("\n")) {
          sb.append(text);
          continue;
        }
        sb.append(text).append("\n");
      }
      sb.append("\n}");
      return sb.toString();
    }
  }

  private static final class SwitchRuleExpressionResult implements SwitchRuleResult {
    private final PsiExpression myExpression;

    private SwitchRuleExpressionResult(@NotNull PsiExpression expression) {myExpression = expression;}

    @Override
    public String generate(CommentTracker ct) {
      return ct.textWithComments(myExpression) + ";";
    }
  }

  private static final class SwitchBranch {
    final boolean myIsDefault;
    final List<? extends PsiCaseLabelElement> myCaseExpressions;
    @NotNull final List<? extends PsiElement> myUsedElements; // used elements only for this branch
    private final SwitchRuleResult myRuleResult;

    private SwitchBranch(boolean isDefault,
                         List<? extends PsiCaseLabelElement> caseExpressions,
                         SwitchRuleResult ruleResult,
                         @NotNull List<? extends PsiElement> usedElements) {
      myIsDefault = isDefault;
      myCaseExpressions = caseExpressions;
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
      grabCommentsBeforeColon(label, ct, sb);
      sb.append("->");
      sb.append(myRuleResult.generate(ct));
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
  }

  private static final class OldSwitchStatementBranch {
    final boolean myIsFallthrough;
    final PsiStatement @NotNull [] myStatements;
    final @NotNull PsiSwitchLabelStatement myLabelStatement;
    final @Nullable PsiBreakStatement myBreakStatement;
    @Nullable OldSwitchStatementBranch myPreviousSwitchBranch = null;

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
      return myLabelStatement.isDefaultCase();
    }

    private boolean isFallthrough() {
      return myIsFallthrough;
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
        if (current == null || current.myStatements.length != 0) {
          return withPrevious;
        }
      }
    }

    private List<? extends PsiElement> getUsedElements() {
      return StreamEx.of(getWithFallthroughBranches()).flatMap(branch -> StreamEx.of(branch.getRelatedStatements())).toList();
    }
  }
}

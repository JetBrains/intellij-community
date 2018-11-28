// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.intellij.util.ObjectUtils.tryCast;

public class EnhancedSwitchMigrationInspection extends AbstractBaseJavaLocalInspectionTool {
  private final static SwitchInspection[] ourInspections = new SwitchInspection[]{
    new ReturningSwitch(),
    new VariableAssigningSwitch(),
    new StatementSwitch()
  };

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.getLanguageLevel(holder.getProject()).isAtLeast(LanguageLevel.JDK_12_PREVIEW)) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitSwitchStatement(PsiSwitchStatement statement) {
        SwitchReplacer replacer = getSwitchReplacer(statement);
        if (replacer == null) return;
        PsiElement switchKeyword = statement.getFirstChild();
        holder.registerProblem(switchKeyword, InspectionsBundle.message("inspection.switch.expression.migration.inspection.name"),
                               new ReplaceWithSwitchExpressionFix(replacer.getType()));
      }
    };
  }

  private static void addBranch(List<SwitchBranch> branches,
                                PsiStatement[] statements,
                                int unmatchedCaseIndex,
                                int endIndexExcl,
                                boolean isFallthrough, PsiBreakStatement current) {
    PsiSwitchLabelStatement labelStatement = (PsiSwitchLabelStatement)statements[unmatchedCaseIndex];
    PsiStatement[] branchStatements = Arrays.copyOfRange(statements, unmatchedCaseIndex + 1, endIndexExcl);
    SwitchBranch branch = new SwitchBranch(isFallthrough, branchStatements, labelStatement, current);
    branches.add(branch);
  }

  @Nullable
  private static List<SwitchBranch> extractBranches(@NotNull PsiCodeBlock body) {
    List<SwitchBranch> branches = new ArrayList<>();
    PsiStatement[] statements = body.getStatements();
    int unmatchedCaseIndex = -1;
    for (int i = 0, length = statements.length; i < length; i++) {
      PsiStatement current = statements[i];
      if (current instanceof PsiSwitchLabelStatement) {
        if (unmatchedCaseIndex != -1) {
          boolean isFallthrough = i != 0 && ControlFlowUtils.statementMayCompleteNormally(statements[i - 1]);
          addBranch(branches, statements, unmatchedCaseIndex, i, isFallthrough, null);
        }
        unmatchedCaseIndex = i;
      }
      else if (current instanceof PsiBreakStatement) {
        if (unmatchedCaseIndex == -1) return null;
        addBranch(branches, statements, unmatchedCaseIndex, i, false, (PsiBreakStatement)current);
        unmatchedCaseIndex = -1;
      }
      else if (current instanceof PsiSwitchLabeledRuleStatement) {
        return null;
      }
    }
    // tail
    if (unmatchedCaseIndex != -1) {
      addBranch(branches, statements, unmatchedCaseIndex, statements.length, false, null);
    }
    return branches;
  }


  @Nullable
  private static PsiLocalVariable getVariable(PsiDeclarationStatement declaration) {
    if (declaration == null) return null;
    PsiElement[] declaredElements = declaration.getDeclaredElements();
    if (declaredElements.length != 1) return null;
    PsiLocalVariable variable = tryCast(declaredElements[0], PsiLocalVariable.class);
    if (variable == null) return null;
    return variable;
  }

  @Nullable
  private static SwitchReplacer getSwitchReplacer(PsiSwitchStatement switchStatement) {
    PsiExpression expression = switchStatement.getExpression();
    if (expression == null) return null;
    PsiCodeBlock body = switchStatement.getBody();
    if (body == null) return null;
    List<SwitchBranch> branches = extractBranches(body);
    if (branches == null) return null;
    boolean isExhaustive = isExhaustiveSwitch(branches, expression);
    for (SwitchInspection inspection : ourInspections) {
      SwitchReplacer replacer = inspection.suggestReplacer(switchStatement, branches, isExhaustive);
      if (replacer != null) return replacer;
    }
    return null;
  }

  @NotNull
  private static PsiSwitchBlock generateEnhancedSwitch(@NotNull PsiSwitchStatement switchStatement,
                                                       PsiExpression expressionBeingSwitched,
                                                       List<SwitchExpressionBranch> newBranches,
                                                       CommentTracker mainCommentTracker,
                                                       boolean isExpr) {
    PsiElementFactory factory = JavaPsiFacade.getInstance(switchStatement.getProject()).getElementFactory();
    mainCommentTracker.markUnchanged(expressionBeingSwitched);
    PsiCodeBlock body = switchStatement.getBody();
    assert body != null;
    for (PsiStatement statement : body.getStatements()) {
      mainCommentTracker.markUnchanged(statement);
    }
    StringBuilder sb = new StringBuilder()
      .append("switch(")
      .append(expressionBeingSwitched.getText())
      .append("){\n");
    int size = newBranches.size();
    List<CommentTracker> branchTrackers = new ArrayList<>(newBranches.size());
    for (int i = 0; i < size; i++) {
      SwitchExpressionBranch newBranch = newBranches.get(i);
      CommentTracker ct = new CommentTracker();
      branchTrackers.add(ct);
      sb.append(newBranch.generate(ct));
      if (i != size - 1) {
        sb.append("\n");
      }
    }
    sb.append("\n}");
    PsiSwitchBlock switchBlock;
    if (isExpr) {
      switchBlock = (PsiSwitchBlock)factory.createExpressionFromText(sb.toString(), switchStatement);
    } else {
      switchBlock = (PsiSwitchBlock)factory.createStatementFromText(sb.toString(), switchStatement);
    }
    PsiCodeBlock resultBody = switchBlock.getBody();
    PsiSwitchLabeledRuleStatement[] labeledStatements = PsiTreeUtil.getChildrenOfType(resultBody, PsiSwitchLabeledRuleStatement.class);
    if (labeledStatements == null || labeledStatements.length != size) return switchBlock;
    // restore comments near every branch
    for (int i = 0; i < labeledStatements.length; i++) {
      PsiSwitchLabeledRuleStatement labeledStatement = labeledStatements[i];
      branchTrackers.get(i).insertCommentsBefore(labeledStatement);
    }
    return switchBlock;
  }

  private static boolean isExhaustiveSwitch(List<SwitchBranch> branches, PsiExpression expressionBeingSwitched) {
    for (SwitchBranch branch : branches) {
      if (branch.isDefault()) return true;
    }
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expressionBeingSwitched.getType());
    if (aClass == null || !aClass.isEnum()) return false;
    Set<String> names = StreamEx.of(aClass.getAllFields())
      .select(PsiEnumConstant.class)
      .map(PsiEnumConstant::getName)
      .toSet();
    for (SwitchBranch branch : branches) {
      PsiExpressionList caseValues = branch.myLabelStatement.getCaseValues();
      if (caseValues != null) {
        for (PsiExpression caseLabel : caseValues.getExpressions()) {
          PsiReferenceExpression reference = tryCast(caseLabel, PsiReferenceExpression.class);
          if (reference != null) {
            PsiEnumConstant enumConstant = tryCast(reference.resolve(), PsiEnumConstant.class);
            if (enumConstant != null) {
              names.remove(enumConstant.getName());
            }
          }
        }
      }
    }
    return names.isEmpty();
  }

  private enum ReplacementType {
    Expression("inspection.replace.with.switch.expression.fix.name"),
    Statement("inspection.replace.with.enhanced.switch.statement.fix.name");

    private final String key;

    ReplacementType(String key) {
      this.key = key;
    }

    String getFixName() {
      return InspectionsBundle.message(key);
    }
  }

  private interface SwitchReplacer {
    void replace(@NotNull PsiSwitchStatement switchStatement);

    ReplacementType getType();
  }

  private interface SwitchInspection {
    @Nullable
    SwitchReplacer suggestReplacer(@NotNull PsiSwitchStatement switchStmt, @NotNull List<SwitchBranch> branches, boolean isExhaustive);
  }

  //Right part of switch rule (case labels -> result)
  private interface SwitchRuleResult {
    String generate(CommentTracker ct);
  }

  static class ReplaceWithSwitchExpressionFix implements LocalQuickFix {
    private final ReplacementType myReplacementType;

    ReplaceWithSwitchExpressionFix(ReplacementType replacementType) {myReplacementType = replacementType;}

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return myReplacementType.getFixName();
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiSwitchStatement.class);
      if (switchStatement == null) return;
      SwitchReplacer replacer = getSwitchReplacer(switchStatement);
      if (replacer == null) return;
      replacer.replace(switchStatement);
    }
  }

  private static class ReturningSwitchReplacer implements SwitchReplacer {
    @NotNull final PsiSwitchStatement mySwitchStatement;
    @NotNull final PsiExpression myExpressionBeingSwitched;
    final List<SwitchExpressionBranch> myNewBranches;
    final @Nullable PsiReturnStatement myReturnToDelete;

    private ReturningSwitchReplacer(@NotNull PsiSwitchStatement switchStatement,
                                    @NotNull PsiExpression expressionBeingSwitched,
                                    List<SwitchExpressionBranch> newBranches, @Nullable PsiReturnStatement returnToDelete) {
      mySwitchStatement = switchStatement;
      myExpressionBeingSwitched = expressionBeingSwitched;
      myNewBranches = newBranches;
      myReturnToDelete = returnToDelete;
    }

    @Override
    public void replace(@NotNull PsiSwitchStatement switchStatement) {
      CommentTracker commentTracker = new CommentTracker();
      PsiSwitchBlock replacement = generateEnhancedSwitch(switchStatement, myExpressionBeingSwitched, myNewBranches, commentTracker, true);
      commentTracker.replaceAndRestoreComments(switchStatement, replacement);
      if (myReturnToDelete != null) {
        CommentTracker ct = new CommentTracker();
        PsiExpression returnValue = myReturnToDelete.getReturnValue();
        if (returnValue != null) {
          ct.markUnchanged(returnValue);
        }
        ct.deleteAndRestoreComments(myReturnToDelete);
      }
    }

    @Override
    public ReplacementType getType() {
      return ReplacementType.Expression;
    }
  }

  /**
   * switch (n) {
   *     case 1:
   *         return "a";
   *     case 2:
   *         return "b";
   *     default:
   *         return "?";
   * }
   */
  private static class ReturningSwitch implements SwitchInspection {
    @Nullable
    @Override
    public SwitchReplacer suggestReplacer(@NotNull PsiSwitchStatement switchStmt,
                                          @NotNull List<SwitchBranch> branches,
                                          boolean isExhaustive) {
      PsiExpression expression = switchStmt.getExpression();
      if (expression == null) return null;
      PsiReturnStatement returnAfterSwitch =
        tryCast(PsiTreeUtil.getNextSiblingOfType(switchStmt, PsiStatement.class), PsiReturnStatement.class);
      if (returnAfterSwitch == null && !isExhaustive) return null;
      List<SwitchExpressionBranch> newBranches = new ArrayList<>();
      for (SwitchBranch branch : branches) {
        PsiStatement[] statements = branch.getStatements();
        if (statements.length != 1) return null;
        PsiReturnStatement returnStmt = tryCast(statements[0], PsiReturnStatement.class);
        if (returnStmt == null) return null;
        PsiExpression returnExpr = returnStmt.getReturnValue();
        if (returnExpr == null) return null;
        newBranches.add(new SwitchExpressionBranch(branch.isDefault(), branch.getCaseExpressions(), new SwitchRuleExpressionResult(returnExpr), branch));
      }
      if (!isExhaustive) {
        PsiExpression returnExpr = returnAfterSwitch.getReturnValue();
        if (returnExpr == null) return null;
        newBranches.add(new SwitchExpressionBranch(true, PsiExpression.EMPTY_ARRAY, new SwitchRuleExpressionResult(returnExpr), null));
      }
      return new ReturningSwitchReplacer(switchStmt, expression, newBranches, returnAfterSwitch);
    }
  }

  private static class SwitchExistingVariableReplacer implements SwitchReplacer {
    @NotNull final PsiVariable myVariableToAssign;
    @NotNull final PsiSwitchStatement mySwitchStatement;
    @NotNull final PsiExpression myExpressionBeingSwitched;
    final List<SwitchExpressionBranch> myNewBranches;

    private SwitchExistingVariableReplacer(
      @NotNull PsiVariable variableToAssign,
      @NotNull PsiSwitchStatement switchStatement,
      @NotNull PsiExpression expressionBeingSwitched,
      List<SwitchExpressionBranch> newBranches
    ) {
      myVariableToAssign = variableToAssign;
      mySwitchStatement = switchStatement;
      myExpressionBeingSwitched = expressionBeingSwitched;
      myNewBranches = newBranches;
    }

    @Override
    public void replace(@NotNull PsiSwitchStatement switchStatement) {
      CommentTracker commentTracker = new CommentTracker();
      PsiSwitchBlock replacement = generateEnhancedSwitch(switchStatement, myExpressionBeingSwitched, myNewBranches, commentTracker, true);
      myVariableToAssign.setInitializer((PsiSwitchExpression)replacement);
      commentTracker.delete(switchStatement);
      commentTracker.insertCommentsBefore(myVariableToAssign);
    }

    @Override
    public ReplacementType getType() {
      return ReplacementType.Expression;
    }
  }

  /**
   * int result;
   * switch(s) {
   *   case "a": result = 1; break;
   *   case "b": result = 2; break;
   *   default: result = 0;
   * }
   */
  private static class VariableAssigningSwitch implements SwitchInspection {
    @Nullable
    @Override
    public SwitchReplacer suggestReplacer(@NotNull PsiSwitchStatement switchStmt,
                                          @NotNull List<SwitchBranch> branches,
                                          boolean isExhaustive) {
      PsiExpression expressionBeingSwitched = switchStmt.getExpression();
      if (expressionBeingSwitched == null) return null;
      PsiDeclarationStatement declaration =
        tryCast(PsiTreeUtil.getPrevSiblingOfType(switchStmt, PsiStatement.class), PsiDeclarationStatement.class);
      PsiLocalVariable variable = getVariable(declaration);
      if (variable == null) return null;
      List<SwitchExpressionBranch> newBranches = new ArrayList<>();
      PsiExpression initializer = variable.getInitializer();
      if (!isExhaustive && initializer == null) return null;
      for (SwitchBranch branch : branches) {
        if (branch.myIsFallthrough) {
          return null;
        }
        PsiStatement[] statements = branch.getStatements();
        if (statements.length != 1) return null;
        PsiExpressionStatement expressionStatement = tryCast(statements[0], PsiExpressionStatement.class);
        if (expressionStatement == null) return null;
        PsiAssignmentExpression assign = tryCast(expressionStatement.getExpression(), PsiAssignmentExpression.class);
        if (assign == null) return null;
        if (!ExpressionUtils.isReferenceTo(assign.getLExpression(), variable)) return null;
        PsiExpression rExpression = assign.getRExpression();
        if (rExpression == null) return null;
        newBranches.add(new SwitchExpressionBranch(branch.isDefault(), branch.getCaseExpressions(), new SwitchRuleExpressionResult(rExpression), branch));
      }
      if (!isExhaustive) {
        newBranches.add(new SwitchExpressionBranch(true, PsiExpression.EMPTY_ARRAY, new SwitchRuleExpressionResult(initializer), null));
      }
      return new SwitchExistingVariableReplacer(variable, switchStmt, expressionBeingSwitched, newBranches);
    }
  }

  /**
   * Replaces with enhanced switch statement
   */
  private static class SwitchStatementReplacer implements SwitchReplacer {
    @NotNull final PsiSwitchStatement mySwitchStatement;
    @NotNull final PsiExpression myExpressionBeingSwitched;
    @NotNull final List<SwitchExpressionBranch> myExpressionBranches;

    private SwitchStatementReplacer(@NotNull PsiSwitchStatement switchStatement,
                                    @NotNull PsiExpression expressionBeingSwitched,
                                    @NotNull List<SwitchExpressionBranch> ruleResults) {
      mySwitchStatement = switchStatement;
      myExpressionBeingSwitched = expressionBeingSwitched;
      myExpressionBranches = ruleResults;
    }

    @Override
    public void replace(@NotNull PsiSwitchStatement switchStatement) {
      CommentTracker commentTracker = new CommentTracker();
      PsiSwitchBlock switchBlock = generateEnhancedSwitch(switchStatement, myExpressionBeingSwitched, myExpressionBranches, commentTracker, false);
      commentTracker.replace(switchStatement, switchBlock);
    }

    @Override
    public ReplacementType getType() {
      return ReplacementType.Statement;
    }
  }

  /**
   *  Suggest replacement with enhanced switch statement
   */
  private static class StatementSwitch implements SwitchInspection {
    @Nullable
    @Override
    public SwitchReplacer suggestReplacer(@NotNull PsiSwitchStatement switchStmt,
                                          @NotNull List<SwitchBranch> branches,
                                          boolean isExhaustive) {
      PsiExpression expression = switchStmt.getExpression();
      if (expression == null) return null;
      for (SwitchBranch branch : branches) {
        if (branch.myIsFallthrough) return null;
      }
      List<SwitchExpressionBranch> switchRules = new ArrayList<>();
      for (SwitchBranch branch : branches) {
        PsiStatement[] statements = branch.getStatements();
        switchRules.add(new SwitchExpressionBranch(branch.isDefault(), branch.getCaseExpressions(), new SwitchStatementBranch(statements), branch));
      }
      return new SwitchStatementReplacer(switchStmt, expression, switchRules);
    }
  }

  private static class SwitchStatementBranch implements SwitchRuleResult {
    final PsiStatement[] myResultStatements;

    private SwitchStatementBranch(PsiStatement[] resultStatements) {
      myResultStatements = resultStatements;
    }

    @Override
    public String generate(CommentTracker ct) {
      return StreamEx.of(myResultStatements).map(stmt -> ct.text(stmt)).joining("\n", "{\n", "}");
    }
  }

  private static class SwitchRuleExpressionResult implements SwitchRuleResult {
    private final PsiExpression myExpression;

    private SwitchRuleExpressionResult(PsiExpression expression) {myExpression = expression;}

    @Override
    public String generate(CommentTracker ct) {
      return ct.text(myExpression) + ";";
    }
  }

  private static class SwitchExpressionBranch {
    final boolean myIsDefault;
    final PsiExpression[] myLabelExpressions;
    private final SwitchRuleResult myRuleResult;
    final @Nullable SwitchBranch mySourceBranch;

    private SwitchExpressionBranch(boolean isDefault,
                                   PsiExpression[] labelExpressions,
                                   SwitchRuleResult ruleResult,
                                   @Nullable SwitchBranch sourceBranch) {
      myIsDefault = isDefault;
      myLabelExpressions = labelExpressions;
      myRuleResult = ruleResult;
      mySourceBranch = sourceBranch;
    }

    String generate(CommentTracker ct) {
      StringBuilder sb = new StringBuilder();
      if (myIsDefault) {
        sb.append("default");
      }
      else {
        sb.append("case ");
        int length = myLabelExpressions.length;
        for (int i = 0; i < length; i++) {
          PsiExpression labelExpression = myLabelExpressions[i];
          sb.append(ct.text(labelExpression));
          if (i != length - 1) {
            sb.append(",");
          }
        }
      }
      sb.append("->");
      sb.append(myRuleResult.generate(ct));
      if (mySourceBranch != null) {
        for (PsiStatement relatedStatement : mySourceBranch.getRelatedStatements()) {
          ct.grabComments(relatedStatement);
        }
      }
      return sb.toString();
    }
  }

  private static class SwitchBranch {
    final boolean myIsFallthrough;
    final @NotNull PsiStatement[] myStatements;
    final @NotNull PsiSwitchLabelStatement myLabelStatement;
    final @Nullable PsiBreakStatement myBreakStatement;

    private SwitchBranch(boolean isFallthrough,
                         @NotNull PsiStatement[] statements,
                         @NotNull PsiSwitchLabelStatement switchLabelStatement,
                         @Nullable PsiBreakStatement breakStatement) {
      myIsFallthrough = isFallthrough;
      myStatements = statements;
      myLabelStatement = switchLabelStatement;
      myBreakStatement = breakStatement;
    }

    boolean isDefault() {
      return myLabelStatement.isDefaultCase();
    }

    PsiExpression[] getCaseExpressions() {
      PsiExpressionList caseValues = myLabelStatement.getCaseValues();
      if (caseValues == null) return PsiExpression.EMPTY_ARRAY;
      return caseValues.getExpressions();
    }

    /**
     * @return only meaningful statements, without break and case statements
     */
    PsiStatement[] getStatements() {
      return myStatements;
    }

    StreamEx<PsiStatement> getRelatedStatements() {
      StreamEx<PsiStatement> withoutBreak = StreamEx.of(myStatements).prepend(myLabelStatement);
      return withoutBreak.prepend(myBreakStatement);
    }
  }
}

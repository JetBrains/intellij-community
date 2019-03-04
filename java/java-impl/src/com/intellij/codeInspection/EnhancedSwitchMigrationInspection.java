// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class EnhancedSwitchMigrationInspection extends AbstractBaseJavaLocalInspectionTool {
  private final static SwitchConversion[] ourInspections = new SwitchConversion[]{
    EnhancedSwitchMigrationInspection::inspectReturningSwitch,
    EnhancedSwitchMigrationInspection::inspectVariableAssigningSwitch,
    EnhancedSwitchMigrationInspection::inspectReplacementWithStatement
  };

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightUtil.Feature.ENHANCED_SWITCH.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitSwitchStatement(PsiSwitchStatement statement) {
        SwitchReplacer replacer = findSwitchReplacer(statement);
        if (replacer == null) return;
        PsiElement switchKeyword = statement.getFirstChild();
        holder.registerProblem(switchKeyword, InspectionsBundle.message(
          "inspection.switch.expression.migration.inspection.switch.description"),
                               new ReplaceWithSwitchExpressionFix(replacer.getType()));
      }
    };
  }

  @Nullable
  private static SwitchReplacer runInspections(PsiStatement statement,
                                               PsiExpression condition,
                                               boolean isExhaustive,
                                               List<? extends OldSwitchStatementBranch> branches) {
    for (SwitchConversion inspection : ourInspections) {
      SwitchReplacer replacer = inspection.suggestReplacer(statement, condition, branches, isExhaustive);
      if (replacer != null) return replacer;
    }
    return null;
  }

  private static OldSwitchStatementBranch addBranch(List<OldSwitchStatementBranch> branches,
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
  private static List<OldSwitchStatementBranch> extractBranches(@NotNull PsiCodeBlock body) {
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

  @Nullable
  private static PsiLocalVariable getVariable(PsiDeclarationStatement declaration) {
    if (declaration == null) return null;
    PsiElement[] declaredElements = declaration.getDeclaredElements();
    if (declaredElements.length != 1) return null;
    return tryCast(declaredElements[0], PsiLocalVariable.class);
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
    List<OldSwitchStatementBranch> branches = extractBranches(body);
    if (branches == null || branches.isEmpty()) return null;
    boolean isExhaustive = isExhaustiveSwitch(branches, expression);
    return runInspections(switchStatement, expression, isExhaustive, branches);
  }

  @Nullable
  private static PsiSwitchBlock generateEnhancedSwitch(@NotNull PsiStatement statementToReplace,
                                                       PsiExpression expressionBeingSwitched,
                                                       List<SwitchExpressionBranch> newBranches,
                                                       CommentTracker mainCommentTracker,
                                                       boolean isExpr) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(statementToReplace.getProject());
    mainCommentTracker.markUnchanged(expressionBeingSwitched);
    if (!(statementToReplace instanceof PsiSwitchStatement)) return null;
    PsiCodeBlock body = ((PsiSwitchStatement)statementToReplace).getBody();
    if (body == null) return null;
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
      switchBlock = (PsiSwitchBlock)factory.createExpressionFromText(sb.toString(), statementToReplace);
    }
    else {
      switchBlock = (PsiSwitchBlock)factory.createStatementFromText(sb.toString(), statementToReplace);
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

  private static boolean isExhaustiveSwitch(List<OldSwitchStatementBranch> branches, PsiExpression expressionBeingSwitched) {
    for (OldSwitchStatementBranch branch : branches) {
      if (branch.isDefault()) return true;
    }
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(expressionBeingSwitched.getType());
    if (aClass == null || !aClass.isEnum()) return false;
    Set<String> names = StreamEx.of(aClass.getAllFields())
      .select(PsiEnumConstant.class)
      .map(PsiEnumConstant::getName)
      .toSet();
    for (OldSwitchStatementBranch branch : branches) {
      for (PsiExpression caseValue : branch.getCurrentBranchCaseExpressions()) {
        PsiReferenceExpression reference = tryCast(caseValue, PsiReferenceExpression.class);
        if (reference != null) {
          PsiEnumConstant enumConstant = tryCast(reference.resolve(), PsiEnumConstant.class);
          if (enumConstant != null) {
            names.remove(enumConstant.getName());
          }
        }
      }
    }
    return names.isEmpty();
  }

  private static boolean isConvertibleBranch(OldSwitchStatementBranch branch) {
    int length = branch.getStatements().length;
    if (length == 0) return branch.isFallthrough();
    return length == 1 && !branch.isFallthrough();
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

  public interface SwitchReplacer {
    void replace(@NotNull PsiStatement switchStatement);

    ReplacementType getType();
  }

  private interface SwitchConversion {
    @Nullable
    SwitchReplacer suggestReplacer(@NotNull PsiStatement statement,
                                   @NotNull PsiExpression expressionBeingSwitched,
                                   @NotNull List<? extends OldSwitchStatementBranch> branches,
                                   boolean isExhaustive
    );
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

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiSwitchStatement statement = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiSwitchStatement.class);
      if (statement == null) return;
      SwitchReplacer replacer = findSwitchReplacer(statement);
      if (replacer == null) return;
      replacer.replace(statement);
    }
  }

  private static class ReturningSwitchReplacer implements SwitchReplacer {
    @NotNull final PsiStatement myStatement;
    @NotNull final PsiExpression myExpressionBeingSwitched;
    final List<SwitchExpressionBranch> myNewBranches;
    final @Nullable PsiReturnStatement myReturnToDelete;

    private ReturningSwitchReplacer(@NotNull PsiStatement statement,
                                    @NotNull PsiExpression expressionBeingSwitched,
                                    List<SwitchExpressionBranch> newBranches, @Nullable PsiReturnStatement returnToDelete) {
      myStatement = statement;
      myExpressionBeingSwitched = expressionBeingSwitched;
      myNewBranches = newBranches;
      myReturnToDelete = returnToDelete;
    }

    @Override
    public void replace(@NotNull PsiStatement statement) {
      CommentTracker commentTracker = new CommentTracker();
      PsiSwitchBlock switchBlock = generateEnhancedSwitch(statement, myExpressionBeingSwitched, myNewBranches, commentTracker, true);
      if (switchBlock == null) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(statement.getProject());
      PsiStatement returnStatement = factory.createStatementFromText("return " + switchBlock.getText() + ";", switchBlock);
      commentTracker.replaceAndRestoreComments(statement, returnStatement);
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
   * case 1:
   * return "a";
   * case 2:
   * return "b";
   * default:
   * return "?";
   * }
   */

  @Nullable
  private static SwitchReplacer inspectReturningSwitch(@NotNull PsiStatement statement,
                                               @NotNull PsiExpression expressionBeingSwitched,
                                               @NotNull List<? extends OldSwitchStatementBranch> branches,
                                               boolean isExhaustive) {
    PsiReturnStatement returnAfterSwitch =
      tryCast(PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class), PsiReturnStatement.class);
    if (returnAfterSwitch == null && !isExhaustive) return null;
    List<SwitchExpressionBranch> newBranches = new ArrayList<>();
    for (OldSwitchStatementBranch branch : branches) {
      if (!isConvertibleBranch(branch)) return null;
      if (branch.isFallthrough()) continue;
      PsiStatement[] statements = branch.getStatements();
      if (statements.length != 1) return null;
      PsiReturnStatement returnStmt = tryCast(statements[0], PsiReturnStatement.class);
      SwitchRuleResult result;
      if (returnStmt == null) {
        PsiThrowStatement throwStatement = tryCast(statements[0], PsiThrowStatement.class);
        if (throwStatement == null) return null;
        result = new SwitchStatementBranch(new PsiStatement[]{throwStatement});
      } else {
        PsiExpression returnExpr = returnStmt.getReturnValue();
        if (returnExpr == null) return null;
        result = new SwitchRuleExpressionResult(returnExpr);
      }
      newBranches.add(new SwitchExpressionBranch(branch.isDefault(),
                                                 branch.getCaseExpressions(),
                                                 result,
                                                 branch.getUsedElements()));
    }
    if (!isExhaustive) {
      PsiExpression returnExpr = returnAfterSwitch.getReturnValue();
      if (returnExpr == null) return null;
      newBranches.add(new SwitchExpressionBranch(true,
                                                 Collections.emptyList(),
                                                 new SwitchRuleExpressionResult(returnExpr),
                                                 Collections.emptyList()));
    }
    return new ReturningSwitchReplacer(statement, expressionBeingSwitched, newBranches, returnAfterSwitch);
  }

  private static class SwitchExistingVariableReplacer implements SwitchReplacer {
    @NotNull final PsiVariable myVariableToAssign;
    @NotNull final PsiStatement myStatement;
    @NotNull final PsiExpression myExpressionBeingSwitched;
    final List<SwitchExpressionBranch> myNewBranches;

    private SwitchExistingVariableReplacer(
      @NotNull PsiVariable variableToAssign,
      @NotNull PsiStatement statement,
      @NotNull PsiExpression expressionBeingSwitched,
      List<SwitchExpressionBranch> newBranches
    ) {
      myVariableToAssign = variableToAssign;
      myStatement = statement;
      myExpressionBeingSwitched = expressionBeingSwitched;
      myNewBranches = newBranches;
    }

    @Override
    public void replace(@NotNull PsiStatement switchStatement) {
      PsiLabeledStatement labeledStatement = tryCast(switchStatement.getParent(), PsiLabeledStatement.class);
      CommentTracker commentTracker = new CommentTracker();
      PsiSwitchBlock replacement = generateEnhancedSwitch(switchStatement, myExpressionBeingSwitched, myNewBranches, commentTracker, true);
      PsiExpression initializer = myVariableToAssign.getInitializer();
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

    @Override
    public ReplacementType getType() {
      return ReplacementType.Expression;
    }
  }

  /**
   * int result;
   * switch(s) {
   * case "a": result = 1; break;
   * case "b": result = 2; break;
   * default: result = 0;
   * }
   */
  @Nullable
  private static SwitchReplacer inspectVariableAssigningSwitch(@NotNull PsiStatement statement,
                                                              @NotNull PsiExpression expressionBeingSwitched,
                                                              @NotNull List<? extends OldSwitchStatementBranch> branches,
                                                              boolean isExhaustive) {
    PsiElement parent = statement.getParent();
    PsiElement anchor = parent instanceof PsiLabeledStatement ? parent : statement;
    PsiDeclarationStatement declaration = tryCast(PsiTreeUtil.getPrevSiblingOfType(anchor, PsiStatement.class), PsiDeclarationStatement.class);
    PsiLocalVariable variable = getVariable(declaration);
    if (variable == null) return null;
    List<SwitchExpressionBranch> newBranches = new ArrayList<>();
    PsiExpression initializer = variable.getInitializer();
    if (!isExhaustive && initializer == null) return null;
    for (OldSwitchStatementBranch branch : branches) {
      if (!isConvertibleBranch(branch)) return null;
      if (branch.isFallthrough() && branch.getStatements().length == 0) continue;
      PsiStatement first = branch.getStatements()[0];
      PsiExpression rExpression = ExpressionUtils.getAssignmentTo(first, variable);
      SwitchRuleResult result;
      if (rExpression == null) {
        PsiThrowStatement throwStatement = tryCast(first, PsiThrowStatement.class);
        if (throwStatement == null) return null;
        result = new SwitchStatementBranch(new PsiStatement[]{throwStatement});
      } else {
        result = new SwitchRuleExpressionResult(rExpression);
      }
      newBranches.add(new SwitchExpressionBranch(branch.isDefault(), branch.getCaseExpressions(), result, branch.getRelatedStatements()));
    }
    if (!isExhaustive) {
      newBranches.add(new SwitchExpressionBranch(true,
                                                 Collections.emptyList(),
                                                 new SwitchRuleExpressionResult(initializer),
                                                 Collections.emptyList()));
    }
    return new SwitchExistingVariableReplacer(variable, statement, expressionBeingSwitched, newBranches);
  }

  /**
   * Replaces with enhanced switch statement
   */
  private static class SwitchStatementReplacer implements SwitchReplacer {
    @NotNull final PsiStatement myStatement;
    @NotNull final PsiExpression myExpressionBeingSwitched;
    @NotNull final List<SwitchExpressionBranch> myExpressionBranches;

    private SwitchStatementReplacer(@NotNull PsiStatement statement,
                                    @NotNull PsiExpression expressionBeingSwitched,
                                    @NotNull List<SwitchExpressionBranch> ruleResults) {
      myStatement = statement;
      myExpressionBeingSwitched = expressionBeingSwitched;
      myExpressionBranches = ruleResults;
    }

    @Override
    public void replace(@NotNull PsiStatement switchStatement) {
      CommentTracker commentTracker = new CommentTracker();
      PsiSwitchBlock switchBlock =
        generateEnhancedSwitch(switchStatement, myExpressionBeingSwitched, myExpressionBranches, commentTracker, false);
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
                                                              @NotNull PsiExpression expressionBeingSwitched,
                                                              @NotNull List<? extends OldSwitchStatementBranch> branches,
                                                              boolean isExhaustive) {
    for (OldSwitchStatementBranch branch : branches) {
      if (!isConvertibleBranch(branch)) return null;
    }
    List<SwitchExpressionBranch> switchRules = new ArrayList<>();
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
      if (statements.length == 1) {
        PsiStatement first = statements[0];
        if (!(first instanceof PsiExpressionStatement || first instanceof PsiBlockStatement || first instanceof PsiThrowStatement)) return null;
      }
      switchRules.add(new SwitchExpressionBranch(branch.isDefault(), branch.getCaseExpressions(),
                                                 new SwitchStatementBranch(statements),
                                                 branch.getRelatedStatements()));
    }
    return new SwitchStatementReplacer(statement, expressionBeingSwitched, switchRules);
  }


  private static boolean isInBranchOrOutside(@NotNull PsiStatement switchStmt,
                                             OldSwitchStatementBranch branch, PsiLocalVariable variable) {
    return !PsiTreeUtil.isAncestor(switchStmt, variable, false)
           || StreamEx.of(branch.getStatements()).anyMatch(stmt -> PsiTreeUtil.isAncestor(stmt, variable, false));
  }

  private static class SwitchStatementBranch implements SwitchRuleResult {
    final PsiStatement[] myResultStatements;

    private SwitchStatementBranch(PsiStatement[] resultStatements) {
      myResultStatements = resultStatements;
    }

    @Override
    public String generate(CommentTracker ct) {
      if (myResultStatements.length == 1) {
        return ct.text(myResultStatements[0]);
      }
      return StreamEx.of(myResultStatements).map(ct::text).joining("\n", "{\n", "}");
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
    final List<PsiExpression> myCaseExpressions;
    @NotNull final List<? extends PsiElement> myUsedElements; // used elements only for this branch
    private final SwitchRuleResult myRuleResult;

    private SwitchExpressionBranch(boolean isDefault,
                                   List<PsiExpression> caseExpressions,
                                   SwitchRuleResult ruleResult,
                                   @NotNull List<? extends PsiElement> usedElements) {
      myIsDefault = isDefault;
      myCaseExpressions = caseExpressions;
      myRuleResult = ruleResult;
      myUsedElements = usedElements;
    }

    String generate(CommentTracker ct) {
      StringBuilder sb = new StringBuilder();
      if (myIsDefault) {
        sb.append("default");
      }
      else {
        sb.append("case ");
        sb.append(StreamEx.of(myCaseExpressions).map(ct::text).joining(","));
      }
      sb.append("->");
      sb.append(myRuleResult.generate(ct));
      for (PsiElement relatedElements : myUsedElements) {
        ct.grabComments(relatedElements);
      }
      return sb.toString();
    }
  }

  private static class OldSwitchStatementBranch {
    final boolean myIsFallthrough;
    final @NotNull PsiStatement[] myStatements;
    final @NotNull PsiSwitchLabelStatement myLabelStatement;
    final @Nullable PsiBreakStatement myBreakStatement;
    @Nullable OldSwitchStatementBranch myPreviousSwitchBranch = null;

    private OldSwitchStatementBranch(boolean isFallthrough,
                                     @NotNull PsiStatement[] statements,
                                     @NotNull PsiSwitchLabelStatement switchLabelStatement,
                                     @Nullable PsiBreakStatement breakStatement) {
      myIsFallthrough = isFallthrough;
      myStatements = statements;
      myLabelStatement = switchLabelStatement;
      myBreakStatement = breakStatement;
    }

    public boolean isDefault() {
      return myLabelStatement.isDefaultCase();
    }

    public boolean isFallthrough() {
      return myIsFallthrough;
    }

    // Case expressions in code order, for all branches
    public List<PsiExpression> getCaseExpressions() {
      List<OldSwitchStatementBranch> branches = getWithFallthroughBranches();
      Collections.reverse(branches);
      return StreamEx.of(branches).flatMap(branch -> {
        PsiExpressionList caseValues = branch.myLabelStatement.getCaseValues();
        if (caseValues == null) return StreamEx.empty();
        return StreamEx.of(caseValues.getExpressions());
      }).toList();
    }

    public List<PsiExpression> getCurrentBranchCaseExpressions() {
      PsiExpressionList caseValues = myLabelStatement.getCaseValues();
      if (caseValues == null) {
        return Collections.emptyList();
      }
      return Arrays.asList(caseValues.getExpressions());
    }

    /**
     * @return only meaningful statements, without break and case statements
     */
    public PsiStatement[] getStatements() {
      return myStatements;
    }

    public List<PsiStatement> getRelatedStatements() {
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

    public List<? extends PsiElement> getUsedElements() {
      return StreamEx.of(getWithFallthroughBranches()).flatMap(branch -> StreamEx.of(branch.getRelatedStatements())).toList();
    }
  }
}

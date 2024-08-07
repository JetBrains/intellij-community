// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

import static com.intellij.util.ObjectUtils.tryCast;

public final class EnhancedSwitchBackwardMigrationInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
        if (!isNonemptyRuleFormatSwitch(expression)) return;
        if (findReplacer(expression) == null) return;
        String message = JavaBundle.message("inspection.switch.expression.backward.expression.migration.inspection.name");
        holder.registerProblem(expression.getFirstChild(), message, new ReplaceWithOldStyleSwitchFix());
      }

      @Override
      public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
        if (!isNonemptyRuleFormatSwitch(statement)) return;
        if (findReplacer(statement) == null) return;
        String message = JavaBundle.message("inspection.switch.expression.backward.statement.migration.inspection.name");
        holder.registerProblem(statement.getFirstChild(), message, new ReplaceWithOldStyleSwitchFix());
      }

      private static boolean isNonemptyRuleFormatSwitch(PsiSwitchBlock block) {
        PsiSwitchLabelStatementBase label = PsiTreeUtil.getChildOfType(block.getBody(), PsiSwitchLabelStatementBase.class);
        return label instanceof PsiSwitchLabeledRuleStatement;
      }
    };
  }

  private static Replacer findReplacer(@NotNull PsiSwitchBlock block) {
    Replacer replacer;

    replacer = inspectReturningSwitch(block);
    if (replacer != null) return replacer;

    replacer = inspectVariableSavingSwitch(block);
    if (replacer != null) return replacer;

    replacer = inspectSwitchStatement(block);
    if (replacer != null) return replacer;

    replacer = inspectAssignmentSwitch(block);
    if (replacer != null) return replacer;

    return null;
  }

  private static Replacer inspectReturningSwitch(@NotNull PsiSwitchBlock switchBlock) {
    if (!(switchBlock instanceof PsiSwitchExpression)) {
      return null;
    }
    Object returnStatement = switchBlock.getParent();
    return returnStatement instanceof PsiReturnStatement ? new ReturningReplacer((PsiReturnStatement)returnStatement) : null;
  }

  private static Replacer inspectVariableSavingSwitch(@NotNull PsiSwitchBlock switchBlock) {
    if (!(switchBlock instanceof PsiSwitchExpression)) return null;
    PsiLocalVariable variable = tryCast(switchBlock.getParent(), PsiLocalVariable.class);
    if (variable == null) return null;
    return new VariableSavingReplacer(variable);
  }

  private static Replacer inspectAssignmentSwitch(@NotNull PsiSwitchBlock switchBlock) {
    if (!(switchBlock instanceof PsiSwitchExpression)) return null;
    PsiAssignmentExpression assignment = tryCast(switchBlock.getParent(), PsiAssignmentExpression.class);
    if (assignment == null || !(assignment.getLExpression() instanceof PsiReferenceExpression)) return null;
    return new AssignmentReplacer(assignment);
  }

  private static Replacer inspectSwitchStatement(@NotNull PsiSwitchBlock switchBlock) {
    if (!(switchBlock instanceof PsiSwitchStatement)) return null;
    return new SwitchStatementReplacer();
  }

  private interface Replacer {
    void replace(PsiSwitchBlock block);
  }

  private static class ReplaceWithOldStyleSwitchFix extends PsiUpdateModCommandQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.replace.with.old.style.switch.statement.fix.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiSwitchBlock switchBlock = tryCast(element instanceof PsiSwitchBlock ? element : element.getParent(), PsiSwitchBlock.class);
      if (switchBlock == null) return;
      Replacer replacer = findReplacer(switchBlock);
      if (replacer == null) return;
      replacer.replace(switchBlock);
    }
  }

  private static final class ReturningReplacer implements Replacer {
    private final PsiReturnStatement myReturnStatement;

    private ReturningReplacer(PsiReturnStatement returnStatement) { myReturnStatement = returnStatement; }

    @Override
    public void replace(PsiSwitchBlock block) {
      CommentTracker ct = new CommentTracker();
      PsiSwitchStatement switchStatement = new ReturnSwitchGenerator(block).generate(ct);
      if (switchStatement == null) return;
      ct.markUnchanged(block);
      ct.replaceAndRestoreComments(myReturnStatement, switchStatement);
    }
  }

  private static final class VariableSavingReplacer implements Replacer {
    private final PsiLocalVariable myVariable;

    private VariableSavingReplacer(PsiLocalVariable variable) {
      myVariable = variable;
    }

    @Override
    public void replace(PsiSwitchBlock block) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(block.getProject());
      PsiTypesUtil.replaceWithExplicitType(myVariable.getTypeElement());
      CommentTracker ct = new CommentTracker();
      PsiSwitchStatement switchStatement = new VarSavingSwitchGenerator(block, myVariable.getName(), "=").generate(ct);
      ct.markUnchanged(block);
      PsiDeclarationStatement variableDeclaration =
        (PsiDeclarationStatement)factory.createStatementFromText(myVariable.getTypeElement().getText() + " " + myVariable.getName() + ";",
                                                                 myVariable);

      ct.markUnchanged(switchStatement);
      PsiStatement declaration = (PsiStatement)ct.replaceAndRestoreComments(myVariable.getParent(), variableDeclaration);

      BlockUtils.addAfter(declaration, switchStatement);
    }
  }

  private static final class AssignmentReplacer implements Replacer {
    private final @NotNull PsiAssignmentExpression myAssignment;

    private AssignmentReplacer(@NotNull PsiAssignmentExpression assignment) {
      myAssignment = assignment;
    }

    @Override
    public void replace(PsiSwitchBlock block) {
      PsiExpression expression = myAssignment.getLExpression();
      if (!(expression instanceof PsiReferenceExpression)) return;
      CommentTracker ct = new CommentTracker();
      String sign = myAssignment.getOperationSign().getText();
      PsiSwitchStatement switchStatement = new VarSavingSwitchGenerator(block, expression.getText(), sign).generate(ct);
      ct.replaceAndRestoreComments(myAssignment.getParent(), switchStatement);
    }
  }

  private static class SwitchStatementReplacer implements Replacer {
    @Override
    public void replace(PsiSwitchBlock block) {
      CommentTracker ct = new CommentTracker();
      PsiSwitchStatement switchStatement = new SwitchStatementGenerator(block).generate(ct);
      if (switchStatement == null) return;
      PsiElement newStatement = block.replace(switchStatement);
      ct.insertCommentsBefore(newStatement);
    }
  }

  private static abstract class SwitchGenerator {
    private final PsiSwitchBlock mySwitchBlock;
    final PsiElementFactory myFactory;

    SwitchGenerator(PsiSwitchBlock switchBlock) {
      mySwitchBlock = switchBlock;
      myFactory = JavaPsiFacade.getElementFactory(mySwitchBlock.getProject());
    }

    PsiSwitchStatement generate(CommentTracker mainCommentTracker) {
      PsiSwitchBlock switchCopy = (PsiSwitchBlock)mySwitchBlock.copy();
      PsiExpression expression = switchCopy.getExpression();
      if (expression == null) return null;
      PsiCodeBlock body = switchCopy.getBody();
      if (body == null) return null;
      List<PsiSwitchLabeledRuleStatement> rules = ContainerUtil.filterIsInstance(body.getStatements(), PsiSwitchLabeledRuleStatement.class);
      List<CommentTracker> branchTrackers = new ArrayList<>();
      IntList caseCounts = new IntArrayList();
      StringJoiner joiner = new StringJoiner("\n");
      boolean addDefaultBranch = mySwitchBlock instanceof PsiSwitchExpression;
      if (mySwitchBlock.getExpression() != null) {
        addDefaultBranch &= !TypeConversionUtil.isBooleanType(mySwitchBlock.getExpression().getType());
      }
      for (int i = 0; i < rules.size(); i++) {
        PsiSwitchLabeledRuleStatement rule = rules.get(i);
        CommentTracker ct = new CommentTracker();
        branchTrackers.add(ct);
        String generate = collectCommentsBefore(rule, mainCommentTracker);
        generate += generateBranch(rule, ct, switchCopy);
        if (i == rules.size() - 1) {
          generate += collectCommentsBefore(body.getRBrace(), mainCommentTracker);
        }
        PsiCaseLabelElementList labelElementList = rule.getCaseLabelElementList();
        int caseCount = labelElementList == null ? 1 : labelElementList.getElementCount();
        caseCounts.add(caseCount);
        if (joiner.length() != 0 && generate.startsWith("\n")) {
          generate = generate.substring(1);
        }
        joiner.add(generate);
        mainCommentTracker.markUnchanged(rule);
        addDefaultBranch &= !SwitchUtils.isDefaultLabel(rule);
      }
      if (addDefaultBranch) {
        joiner.add("default:throw new java.lang.IllegalArgumentException();");
      }
      String bodyText = joiner.toString();
      String switchText = "switch(" + mainCommentTracker.text(expression) + "){" + bodyText + "}";
      mainCommentTracker.grabComments(switchCopy);
      PsiSwitchStatement newBlock = (PsiSwitchStatement)myFactory.createStatementFromText(switchText, mySwitchBlock);
      PsiCodeBlock newBody = newBlock.getBody();
      assert newBody != null;
      List<PsiSwitchLabelStatement> branches = ContainerUtil.filterIsInstance(newBody.getStatements(), PsiSwitchLabelStatement.class);
      int totalCaseStatements = 0;
      for (int i = 0; i < caseCounts.size(); i++) {
        totalCaseStatements += caseCounts.getInt(i);
      }
      if (branches.size() != totalCaseStatements) return newBlock;
      int firstCaseInChainIndex = 0;
      for (int i = 0; i < branchTrackers.size(); i++) {
        PsiSwitchLabelStatement branch = branches.get(firstCaseInChainIndex);
        branchTrackers.get(i).insertCommentsBefore(branch);
        firstCaseInChainIndex += caseCounts.getInt(i);
      }
      return newBlock;
    }

    @NotNull
    private static String collectCommentsBefore(@Nullable PsiElement rule, @NotNull CommentTracker ct) {
      boolean commentFound = false;
      if (rule == null) {
        return "";
      }
      List<String> lists = new ArrayList<>();
      PsiElement previous = rule.getPrevSibling();
      while (true) {
        if (previous instanceof PsiComment || previous instanceof PsiWhiteSpace) {
          if (previous instanceof PsiComment) {
            commentFound = true;
          }
          lists.add(ct.text(previous));
          previous = previous.getPrevSibling();
        }
        else {
          break;
        }
      }
      if (!commentFound) {
        return "";
      }
      Collections.reverse(lists);
      return String.join("", lists);
    }

    // rule changes inside, must be copied
    private String generateBranch(PsiSwitchLabeledRuleStatement rule,
                                  CommentTracker ct,
                                  PsiSwitchBlock switchBlock) {
      StreamEx.ofTree((PsiElement)rule, el -> StreamEx.of(el.getChildren()))
        .select(PsiYieldStatement.class)
        .filter(statement -> statement.getExpression() != null && statement.findEnclosingExpression() == switchBlock)
        .forEach(statement -> handleYieldInside(statement, ct));
      PsiCaseLabelElementList labelElementList = rule.getCaseLabelElementList();
      String caseExpressionsText;
      if (labelElementList == null || labelElementList.getElementCount() == 0) {
        if (SwitchUtils.isDefaultLabel(rule)) {
          caseExpressionsText = "default:";
        }
        else {
          caseExpressionsText = "case:";
        }
      }
      else {
        PsiCaseLabelElement[] labelElements = labelElementList.getElements();
        if (ContainerUtil.exists(labelElements, label -> label instanceof PsiPattern)) {
          //to preserve the style, when labels are listed separated by commas
          PsiExpression guardExpression = rule.getGuardExpression();
          String guardText = guardExpression == null ? "" : " when " + ct.text(guardExpression);
          caseExpressionsText = StreamEx.of(labelElements)
            .map(e -> ct.text(e))
            .joining(",", "case " , guardText + ":");
        }
        else {
          caseExpressionsText = StreamEx.of(labelElements)
            .map(e -> e instanceof PsiDefaultCaseLabelElement ?
                      (ct.text(e) + ":") :
                      ("case " + ct.text(e) + ":"))
            .joining("\n");
        }
      }
      PsiStatement body = rule.getBody();
      String finalBody;
      if (body == null) {
        finalBody = "";
      }
      else if (!(body instanceof PsiBlockStatement)) {
        finalBody = generateExpressionBranch(body, ct);
      }
      else {
        finalBody = generateBlockBranch(body, ct);
      }
      ct.grabComments(rule);
      return caseExpressionsText + finalBody;
    }

    String generateBlockBranch(@NotNull PsiStatement statement, CommentTracker ct) {
      if (statement instanceof PsiBlockStatement blockStatement) {
        StringBuilder builder = new StringBuilder();
        PsiCodeBlock block = blockStatement.getCodeBlock();
        for (PsiElement element = block.getLBrace(); element != null && element != block.getRBrace(); element = element.getNextSibling()) {
          if (element == block.getLBrace() || element == block.getRBrace()) {
            continue;
          }
          builder.append(ct.text(element));
        }
        return builder.toString().strip();
      }
      else {
        return ct.text(statement);
      }
    }

    abstract void handleYieldInside(@NotNull PsiYieldStatement yieldStatement, CommentTracker ct);

    abstract String generateExpressionBranch(@NotNull PsiStatement statement, CommentTracker ct);
  }

  private static class ReturnSwitchGenerator extends SwitchGenerator {
    ReturnSwitchGenerator(PsiSwitchBlock switchBlock) {
      super(switchBlock);
    }

    @Override
    void handleYieldInside(@NotNull PsiYieldStatement yieldStatement, CommentTracker ct) {
      PsiExpression valueExpression = yieldStatement.getExpression();
      assert valueExpression != null;
      PsiStatement replacement = myFactory.createStatementFromText("return " + ct.text(valueExpression) + ";", yieldStatement);
      ct.replace(yieldStatement, replacement);
    }

    @Override
    String generateExpressionBranch(@NotNull PsiStatement statement, CommentTracker ct) {
      if (statement instanceof PsiThrowStatement) return ct.text(statement);
      return "return " + ct.text(statement);
    }
  }

  private static class VarSavingSwitchGenerator extends SwitchGenerator {
    private final @NotNull String myReferenceText;
    private final @NotNull String mySign;

    VarSavingSwitchGenerator(PsiSwitchBlock switchBlock, @NotNull String referenceText, @NotNull String sign) {
      super(switchBlock);
      myReferenceText = referenceText;
      mySign = sign;
    }

    @Override
    void handleYieldInside(@NotNull PsiYieldStatement yieldStatement, CommentTracker ct) {
      PsiExpression valueExpression = yieldStatement.getExpression();
      assert valueExpression != null;
      String assignText = myReferenceText + " " + mySign + " " + ct.text(valueExpression) + ";\n";
      PsiStatement assignment = myFactory.createStatementFromText(assignText, valueExpression);
      PsiStatement newAssignment = (PsiStatement)ct.replace(yieldStatement, assignment);
      BlockUtils.addAfter(newAssignment, myFactory.createStatementFromText("break;", null));
    }

    @Override
    String generateExpressionBranch(@NotNull PsiStatement statement, CommentTracker ct) {
      if (statement instanceof PsiThrowStatement) {
        return ct.text(statement);
      }
      return myReferenceText + " " + mySign + " " + ct.text(statement) + "\nbreak;";
    }
  }

  private static class SwitchStatementGenerator extends SwitchGenerator {
    SwitchStatementGenerator(PsiSwitchBlock switchBlock) {
      super(switchBlock);
    }

    @Override
    void handleYieldInside(@NotNull PsiYieldStatement yieldStatement, CommentTracker ct) {
      // impossible, only if code is already broken, it can happen
    }

    @Override
    String generateExpressionBranch(@NotNull PsiStatement statement, CommentTracker ct) {
      if (ControlFlowUtils.statementMayCompleteNormally(statement)) {
        return ct.text(statement) + "\nbreak;";
      }
      return ct.text(statement);
    }

    @Override
    String generateBlockBranch(@NotNull PsiStatement statement, CommentTracker ct) {
      if (ControlFlowUtils.statementMayCompleteNormally(statement)) {
        return super.generateBlockBranch(statement, ct) + "\nbreak;";
      }
      return super.generateBlockBranch(statement, ct);
    }
  }
}
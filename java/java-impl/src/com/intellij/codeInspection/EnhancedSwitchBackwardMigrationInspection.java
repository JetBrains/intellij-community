// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.SwitchUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import static com.intellij.util.ObjectUtils.tryCast;

public class EnhancedSwitchBackwardMigrationInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final SwitchMigrationCase[] ourCases = new SwitchMigrationCase[]{
    EnhancedSwitchBackwardMigrationInspection::inspectReturningSwitch,
    EnhancedSwitchBackwardMigrationInspection::inspectVariableSavingSwitch,
    EnhancedSwitchBackwardMigrationInspection::inspectSwitchStatement
  };

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightUtil.Feature.ENHANCED_SWITCH.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitSwitchExpression(PsiSwitchExpression expression) {
        if (!SwitchUtils.isRuleFormatSwitch(expression)) return;
        if (findReplacer(expression) == null) return;
        String message = InspectionsBundle.message("inspection.switch.expression.backward.expression.migration.inspection.name");
        holder.registerProblem(expression.getFirstChild(), message, new ReplaceWithOldStyleSwitchFix());
      }

      @Override
      public void visitSwitchStatement(PsiSwitchStatement statement) {
        if (!SwitchUtils.isRuleFormatSwitch(statement)) return;
        if (findReplacer(statement) == null) return;
        String message = InspectionsBundle.message("inspection.switch.expression.backward.statement.migration.inspection.name");
        holder.registerProblem(statement.getFirstChild(), message, new ReplaceWithOldStyleSwitchFix());
      }
    };
  }

  private static Replacer findReplacer(@NotNull PsiSwitchBlock block) {
    for (SwitchMigrationCase migrationCase : ourCases) {
      Replacer replacer = migrationCase.suggestReplacer(block);
      if (replacer != null) return replacer;
    }
    return null;
  }

  private static Replacer inspectReturningSwitch(@NotNull PsiSwitchBlock switchBlock) {
    if (!(switchBlock instanceof PsiSwitchExpression)) return null;
    PsiReturnStatement returnStatement = tryCast(switchBlock.getParent(), PsiReturnStatement.class);
    if (returnStatement == null) return null;
    return new ReturningReplacer(returnStatement);
  }

  private static Replacer inspectVariableSavingSwitch(@NotNull PsiSwitchBlock switchBlock) {
    if (!(switchBlock instanceof PsiSwitchExpression)) return null;
    PsiLocalVariable variable = tryCast(switchBlock.getParent(), PsiLocalVariable.class);
    if (variable == null) return null;
    return new VariableSavingReplacer(variable);
  }

  private static Replacer inspectSwitchStatement(@NotNull PsiSwitchBlock switchBlock) {
    if (!(switchBlock instanceof PsiSwitchStatement)) return null;
    return new SwitchStatementReplacer();
  }

  private interface SwitchMigrationCase {
    @Nullable
    Replacer suggestReplacer(@NotNull PsiSwitchBlock switchBlock);
  }

  private interface Replacer {
    void replace(PsiSwitchBlock block);
  }

  private static class ReplaceWithOldStyleSwitchFix implements LocalQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.replace.with.old.style.switch.statement.fix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiSwitchBlock switchBlock = tryCast(descriptor.getStartElement().getParent(), PsiSwitchBlock.class);
      if (switchBlock == null) return;
      Replacer replacer = findReplacer(switchBlock);
      if (replacer == null) return;
      replacer.replace(switchBlock);
    }
  }

  private static class ReturningReplacer implements Replacer {
    private final PsiReturnStatement myReturnStatement;

    private ReturningReplacer(PsiReturnStatement returnStatement) {myReturnStatement = returnStatement;}

    @Override
    public void replace(PsiSwitchBlock block) {
      CommentTracker ct = new CommentTracker();
      PsiSwitchStatement switchStatement = new ReturnSwitchGenerator(block).generate(ct);
      if (switchStatement == null) return;
      ct.markUnchanged(block);
      ct.replaceAndRestoreComments(myReturnStatement, switchStatement);
    }
  }

  private static class VariableSavingReplacer implements Replacer {
    private final PsiLocalVariable myVariable;

    private VariableSavingReplacer(PsiLocalVariable variable) {
      myVariable = variable;
    }

    @Override
    public void replace(PsiSwitchBlock block) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(block.getProject());
      PsiTypesUtil.replaceWithExplicitType(myVariable.getTypeElement());
      CommentTracker ct = new CommentTracker();
      PsiSwitchStatement switchStatement = new VarSavingSwitchGenerator(block, myVariable).generate(ct);
      ct.markUnchanged(block);
      PsiDeclarationStatement variableDeclaration =
        (PsiDeclarationStatement)factory.createStatementFromText(myVariable.getTypeElement().getText() + " " + myVariable.getName() + ";", myVariable);

      ct.markUnchanged(switchStatement);
      PsiStatement declaration = (PsiStatement)ct.replaceAndRestoreComments(myVariable.getParent(), variableDeclaration);

      BlockUtils.addAfter(declaration, switchStatement);
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

    SwitchGenerator(PsiSwitchBlock switchBlock) {mySwitchBlock = switchBlock;
      myFactory = JavaPsiFacade.getElementFactory(mySwitchBlock.getProject());
    }

    PsiSwitchStatement generate(CommentTracker mainCommentTracker) {
      PsiSwitchBlock switchCopy = (PsiSwitchBlock)mySwitchBlock.copy();
      PsiExpression expression = switchCopy.getExpression();
      if (expression == null) return null;
      PsiCodeBlock body = switchCopy.getBody();
      if (body == null) return null;
      List<PsiSwitchLabeledRuleStatement> rules = StreamEx.of(body.getStatements())
        .select(PsiSwitchLabeledRuleStatement.class)
        .toList();
      List<CommentTracker> branchTrackers = new ArrayList<>();
      StringJoiner joiner = new StringJoiner("\n");
      for (PsiSwitchLabeledRuleStatement rule : rules) {
        CommentTracker ct = new CommentTracker();
        branchTrackers.add(ct);
        String generate = generateBranch(rule, ct, switchCopy);
        joiner.add(generate);
        mainCommentTracker.markUnchanged(rule);
      }
      String bodyText = joiner.toString();
      String switchText = "switch(" + mainCommentTracker.text(expression) + "){" + bodyText + "}";
      mainCommentTracker.grabComments(switchCopy);
      PsiSwitchStatement newBlock = (PsiSwitchStatement)myFactory.createStatementFromText(switchText, mySwitchBlock);
      PsiCodeBlock newBody = newBlock.getBody();
      assert newBody != null;
      List<PsiSwitchLabelStatement> branches = StreamEx.of(newBody.getStatements())
        .select(PsiSwitchLabelStatement.class)
        .toList();
      if (branches.size() != branchTrackers.size()) return newBlock;
      for (int i = 0; i < branches.size(); i++) {
        PsiSwitchLabelStatement branch = branches.get(i);
        branchTrackers.get(i).insertCommentsBefore(branch);
      }
      return newBlock;
    }

    // rule changes inside, must be copied
    private String generateBranch(PsiSwitchLabeledRuleStatement rule,
                                         CommentTracker ct,
                                         PsiSwitchBlock switchBlock) {
      StreamEx.ofTree((PsiElement)rule, el -> StreamEx.of(el.getChildren()))
        .select(PsiBreakStatement.class)
        .filter(breakStatement -> breakStatement.getValueExpression() != null && breakStatement.findExitedElement() == switchBlock)
        .forEach(breakStatement -> handleBreakInside(breakStatement, ct));
      PsiExpressionList caseValues = rule.getCaseValues();
      String caseValuesText = caseValues == null ? "" : ct.text(caseValues);
      PsiStatement body = rule.getBody();
      String finalBody;
      if (body == null) {
        finalBody = "";
      } else if (!(body instanceof PsiBlockStatement)) {
        finalBody = generateExpressionBranch(body, ct);
      } else {
        finalBody = generateBlockBranch(body, ct);
      }
      ct.grabComments(rule);


      String prefix = rule.isDefaultCase() ? "default" : "case " + caseValuesText;
      return prefix + ":" + finalBody;
    }

    String generateBlockBranch(@NotNull PsiStatement statement, CommentTracker ct) {
      return StreamEx.of(ControlFlowUtils.unwrapBlock(statement))
        .map(ct::text)
        .joining("\n");
    }

    abstract void handleBreakInside(@NotNull PsiBreakStatement breakStatement, CommentTracker ct);

    abstract String generateExpressionBranch(@NotNull PsiStatement statement, CommentTracker ct);
  }

  private static class ReturnSwitchGenerator extends SwitchGenerator {
    ReturnSwitchGenerator(PsiSwitchBlock switchBlock) {
      super(switchBlock);
    }

    @Override
    void handleBreakInside(@NotNull PsiBreakStatement breakStatement, CommentTracker ct) {
      PsiExpression valueExpression = breakStatement.getValueExpression();
      assert valueExpression != null;
      PsiStatement replacement = myFactory.createStatementFromText("return " + ct.text(valueExpression) + ";", breakStatement);
      ct.markUnchanged(valueExpression);
      ct.grabComments(breakStatement);
      breakStatement.replace(replacement);
    }

    @Override
    String generateExpressionBranch(@NotNull PsiStatement statement, CommentTracker ct) {
      return "return " + ct.text(statement);
    }
  }

  private static class VarSavingSwitchGenerator extends SwitchGenerator {
    private final @NotNull PsiLocalVariable myVariable;

    VarSavingSwitchGenerator(PsiSwitchBlock switchBlock, @NotNull PsiLocalVariable variable) {
      super(switchBlock);
      myVariable = variable;
    }

    @Override
    void handleBreakInside(@NotNull PsiBreakStatement breakStatement, CommentTracker ct) {
      PsiExpression valueExpression = breakStatement.getValueExpression();
      assert valueExpression != null;
      String assignText = myVariable.getName() + " = " + valueExpression.getText() + ";\n";
      PsiStatement assignment = myFactory.createStatementFromText(assignText, valueExpression);
      ct.markUnchanged(valueExpression);
      ct.grabComments(breakStatement);
      PsiStatement newAssignment = (PsiStatement)breakStatement.replace(assignment);
      BlockUtils.addAfter(newAssignment, myFactory.createStatementFromText("break;", null));
    }

    @Override
    String generateExpressionBranch(@NotNull PsiStatement statement, CommentTracker ct) {
      if (statement instanceof PsiThrowStatement) {
        return ct.text(statement);
      }
      return myVariable.getName() + " = " + ct.text(statement) + "\nbreak;";
    }
  }

  private static class SwitchStatementGenerator extends SwitchGenerator {
    SwitchStatementGenerator(PsiSwitchBlock switchBlock) {
      super(switchBlock);
    }

    @Override
    void handleBreakInside(@NotNull PsiBreakStatement breakStatement, CommentTracker ct) {
      // impossible, only if code is already broken, it can happen
    }

    @Override
    String generateExpressionBranch(@NotNull PsiStatement statement, CommentTracker ct) {
      return ct.text(statement) + "\nbreak;";
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

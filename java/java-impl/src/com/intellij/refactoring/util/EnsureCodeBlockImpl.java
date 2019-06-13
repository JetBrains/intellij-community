// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.intention.impl.SplitConditionUtil;
import com.intellij.codeInsight.intention.impl.SplitDeclarationAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BinaryOperator;

/**
 * {@link RefactoringUtil#ensureCodeBlock(PsiExpression)} method implementation
 */
class EnsureCodeBlockImpl {
  @Nullable
  static <T extends PsiExpression> T ensureCodeBlock(@NotNull T expression) {
    PsiElement parent = RefactoringUtil.getParentStatement(expression, false);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());
    if (parent instanceof PsiExpression) {
      PsiLambdaExpression lambda = (PsiLambdaExpression)parent.getParent();
      expression = replace(expression, parent, (old, copy) -> {
        String replacement = PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType(lambda)) ? "{a;}" : "{return a;}";
        PsiElement block = old.replace(factory.createCodeBlockFromText(replacement, lambda));
        return LambdaUtil.extractSingleExpressionFromBody(block).replace(copy);
      });
      parent = RefactoringUtil.getParentStatement(expression, false);
    }
    else if (parent instanceof PsiStatement) {
      PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiCodeBlock) ||
          (parent instanceof PsiForStatement &&
           PsiTreeUtil.isAncestor(((PsiForStatement)parent).getInitialization(), expression, true) &&
           hasNameCollision(((PsiForStatement)parent).getInitialization(), grandParent))) {
        boolean addBreak = parent instanceof PsiExpressionStatement && grandParent instanceof PsiSwitchLabeledRuleStatement && 
                           ((PsiSwitchLabeledRuleStatement)grandParent).getEnclosingSwitchBlock() instanceof PsiSwitchExpression;
        if (addBreak) {
          expression = replace(expression, parent, (old, copy) -> {
            PsiBlockStatement block = (PsiBlockStatement)old.replace(factory.createStatementFromText("{break a;}", old));
            PsiExpression copyExpression = ((PsiBreakStatement)block.getCodeBlock().getStatements()[0]).getExpression();
            return Objects.requireNonNull(copyExpression).replace(((PsiExpressionStatement)copy).getExpression());
          });
        } else {
          expression = replace(expression, parent, (old, copy) -> {
            PsiBlockStatement blockStatement = (PsiBlockStatement)old.replace(factory.createStatementFromText("{}", old));
            return blockStatement.getCodeBlock().add(copy);
          });
        }
        parent = RefactoringUtil.getParentStatement(expression, false);
      }
    }
    if (parent == null) {
      parent = PsiTreeUtil.getParentOfType(expression, PsiField.class, true, PsiClass.class);
      if (parent == null) return null;
      return replace(expression, parent, (oldParent, copy) -> extractFieldInitializer((PsiField)oldParent, (PsiField)copy));
    }
    
    PsiConditionalExpression ternary = findSurroundingTernary(expression);
    if (ternary != null && parent instanceof PsiStatement) {
      return replace(expression, parent, (oldParent, copy) -> replaceTernaryWithIf((PsiStatement)oldParent, ternary));
    }

    PsiPolyadicExpression condition = findSurroundingConditionChain(expression);
    PsiExpression operand;
    if (condition != null) {
      T finalExpression = expression;
      operand = StreamEx.of(condition.getOperands()).findFirst(op -> PsiTreeUtil.isAncestor(op, finalExpression, false))
        .orElseThrow(AssertionError::new);
    }
    else {
      operand = null;
    }

    if (parent instanceof PsiIfStatement && condition != null && condition.getOperationTokenType().equals(JavaTokenType.ANDAND)) {
      return replace(expression, parent, (oldParent, copy) -> splitIf((PsiIfStatement)oldParent, condition, operand));
    }
    if (parent instanceof PsiWhileStatement) {
      return replace(expression, parent, (oldParent, copy) -> extractWhileCondition((PsiWhileStatement)oldParent, condition, operand));
    }
    if (parent instanceof PsiReturnStatement && condition != null) {
      return replace(expression, parent, (oldParent, copy) -> splitReturn((PsiReturnStatement)oldParent, condition, operand));
    }
    return expression;
  }

  private static <T extends PsiElement> T replace(@NotNull T element, @NotNull PsiElement parent, BinaryOperator<PsiElement> replacer) {
    Object marker = new Object();
    PsiTreeUtil.mark(element, marker);
    PsiElement copy = parent.copy();
    PsiElement newParent = replacer.apply(parent, copy);
    //noinspection unchecked
    return (T)PsiTreeUtil.releaseMark(newParent, marker);
  }

  private static PsiElement splitReturn(@NotNull PsiReturnStatement returnStatement,
                                        @NotNull PsiPolyadicExpression condition,
                                        @NotNull PsiExpression operand) {
    PsiExpression lOperands = SplitConditionUtil.getLOperands(condition, condition.getTokenBeforeOperand(operand));
    PsiExpression rOperands = getRightOperands(condition, operand);
    Project project = returnStatement.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    CommentTracker ct = new CommentTracker();
    boolean orChain = condition.getOperationTokenType().equals(JavaTokenType.OROR);
    String ifText =
      "if(" + (orChain ? ct.text(lOperands) : BoolUtils.getNegatedExpressionText(lOperands, ct)) + ") return " + orChain + ";";
    PsiStatement ifStatement = factory.createStatementFromText(ifText, returnStatement);
    CodeStyleManager.getInstance(project).reformat(returnStatement.getParent().addBefore(ifStatement, returnStatement));
    return ct.replaceAndRestoreComments(Objects.requireNonNull(returnStatement.getReturnValue()), rOperands);
  }

  @NotNull
  private static PsiElement extractFieldInitializer(PsiField field, PsiField copy) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(field.getProject());
    PsiClassInitializer initializer =
      ObjectUtils.tryCast(PsiTreeUtil.skipWhitespacesAndCommentsForward(field), PsiClassInitializer.class);
    boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    if (initializer == null || initializer.hasModifierProperty(PsiModifier.STATIC) != isStatic) {
      initializer = factory.createClassInitializer();
      if (isStatic) {
        Objects.requireNonNull(initializer.getModifierList()).setModifierProperty(PsiModifier.STATIC, true);
      }
      initializer = (PsiClassInitializer)field.getParent().addAfter(initializer, field);
    }
    PsiCodeBlock body = initializer.getBody();
    // There are at least two children: open and close brace
    // we will insert an initializer after the first brace and any whitespace which follow it
    PsiElement anchor = PsiTreeUtil.skipWhitespacesForward(body.getFirstChild());
    assert anchor != null;
    anchor = anchor.getPrevSibling();
    assert anchor != null;

    PsiExpressionStatement assignment =
      (PsiExpressionStatement)factory.createStatementFromText(field.getName() + "=null;", initializer);
    assignment = (PsiExpressionStatement)body.addAfter(assignment, anchor);
    PsiExpression fieldInitializer = copy.getInitializer();
    if (fieldInitializer instanceof PsiArrayInitializerExpression) {
      PsiType fieldType = field.getType();
      if (fieldType instanceof PsiArrayType) {
        fieldInitializer = RefactoringUtil
          .createNewExpressionFromArrayInitializer((PsiArrayInitializerExpression)fieldInitializer, fieldType);
      }
    }
    PsiExpression rExpression = ((PsiAssignmentExpression)assignment.getExpression()).getRExpression();
    assert fieldInitializer != null;
    assert rExpression != null;
    rExpression.replace(fieldInitializer);
    Objects.requireNonNull(field.getInitializer()).delete();
    return assignment;
  }

  private static PsiElement extractWhileCondition(PsiWhileStatement whileStatement,
                                                  PsiPolyadicExpression condition,
                                                  PsiExpression operand) {
    PsiExpression oldCondition = Objects.requireNonNull(whileStatement.getCondition());
    PsiStatement body = whileStatement.getBody();
    PsiBlockStatement blockBody;
    Project project = whileStatement.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    if (body == null) {
      PsiWhileStatement newWhileStatement = (PsiWhileStatement)factory.createStatementFromText("while(true) {}", whileStatement);
      Objects.requireNonNull(newWhileStatement.getCondition()).replace(oldCondition);
      whileStatement = (PsiWhileStatement)whileStatement.replace(newWhileStatement);
      blockBody = (PsiBlockStatement)Objects.requireNonNull(whileStatement.getBody());
      oldCondition = Objects.requireNonNull(whileStatement.getCondition());
    }
    else if (body instanceof PsiBlockStatement) {
      blockBody = (PsiBlockStatement)body;
    }
    else {
      PsiBlockStatement newBody = BlockUtils.createBlockStatement(project);
      newBody.add(body);
      blockBody = (PsiBlockStatement)body.replace(newBody);
    }
    PsiExpression lOperands;
    PsiExpression rOperands;
    if (condition != null && condition.getOperationTokenType().equals(JavaTokenType.ANDAND)) {
      lOperands = SplitConditionUtil.getLOperands(condition, condition.getTokenBeforeOperand(operand));
      rOperands = getRightOperands(condition, operand);
    }
    else {
      lOperands = factory.createExpressionFromText("true", whileStatement);
      rOperands = oldCondition;
    }
    PsiCodeBlock codeBlock = blockBody.getCodeBlock();
    PsiIfStatement ifStatement = (PsiIfStatement)factory.createStatementFromText("if(!true) break;", whileStatement);
    ifStatement = (PsiIfStatement)codeBlock.addAfter(ifStatement, codeBlock.getLBrace());
    PsiPrefixExpression negation = (PsiPrefixExpression)Objects.requireNonNull(ifStatement.getCondition());
    PsiElement newParent = Objects.requireNonNull(negation.getOperand()).replace(rOperands);
    Objects.requireNonNull(whileStatement.getCondition()).replace(lOperands);
    return newParent;
  }
  private static PsiElement replaceTernaryWithIf(PsiStatement statement, PsiConditionalExpression ternary) {
    Project project = statement.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(ternary.getParent());
    if (parent instanceof PsiLocalVariable) {
      PsiLocalVariable variable = (PsiLocalVariable)parent;
      variable.normalizeDeclaration();
      PsiDeclarationStatement declaration = (PsiDeclarationStatement)variable.getParent();
      PsiAssignmentExpression assignment =
        SplitDeclarationAction.invokeOnDeclarationStatement(declaration, PsiManager.getInstance(project), project);
      if (assignment != null) {
        ternary = (PsiConditionalExpression)Objects.requireNonNull(PsiUtil.skipParenthesizedExprDown(assignment.getRExpression()));
        statement = (PsiStatement)assignment.getParent();
      }
    }
    CommentTracker ct = new CommentTracker();
    PsiIfStatement ifStatement =
      (PsiIfStatement)factory.createStatementFromText("if(" + ct.text(ternary.getCondition()) + ") {} else {}", statement);
    Object mark = new Object();
    PsiTreeUtil.mark(ternary, mark);
    for (PsiElement child : statement.getChildren()) {
      if (child instanceof PsiComment) {
        ct.delete(child);
      }
    }
    PsiStatement thenStatement = (PsiStatement)statement.copy();
    PsiConditionalExpression thenTernary = Objects.requireNonNull((PsiConditionalExpression)PsiTreeUtil.releaseMark(thenStatement, mark));
    PsiExpression thenBranch = ternary.getThenExpression();
    if (thenBranch != null) {
      thenTernary.replace(ct.markUnchanged(thenBranch));
    }
    PsiStatement elseStatement = (PsiStatement)statement.copy();
    PsiConditionalExpression elseTernary = Objects.requireNonNull((PsiConditionalExpression)PsiTreeUtil.releaseMark(elseStatement, mark));
    PsiExpression elseBranch = ternary.getElseExpression();
    if (elseBranch != null) {
      elseTernary.replace(ct.markUnchanged(elseBranch));
    }
    ((PsiBlockStatement)Objects.requireNonNull(ifStatement.getThenBranch())).getCodeBlock().add(thenStatement);
    ((PsiBlockStatement)Objects.requireNonNull(ifStatement.getElseBranch())).getCodeBlock().add(elseStatement);
    return ct.replaceAndRestoreComments(statement, ifStatement);
  }

  private static PsiElement splitIf(PsiIfStatement outerIf, PsiPolyadicExpression andChain, PsiExpression operand) {
    PsiExpression lOperands = SplitConditionUtil.getLOperands(andChain, andChain.getTokenBeforeOperand(operand));
    PsiExpression rOperands = getRightOperands(andChain, operand);
    Project project = outerIf.getProject();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiBlockStatement newThenBranch = (PsiBlockStatement)factory.createStatementFromText("{if(true);}", outerIf);
    PsiStatement thenBranch = Objects.requireNonNull(outerIf.getThenBranch());
    Objects.requireNonNull(((PsiIfStatement)newThenBranch.getCodeBlock().getStatements()[0]).getThenBranch()).replace(thenBranch);
    newThenBranch = (PsiBlockStatement)thenBranch.replace(newThenBranch);
    PsiIfStatement innerIf =
      (PsiIfStatement)CodeStyleManager.getInstance(project).reformat(newThenBranch.getCodeBlock().getStatements()[0]);
    PsiElement newParent = Objects.requireNonNull(innerIf.getCondition()).replace(rOperands);
    andChain.replace(lOperands);
    return newParent;
  }

  private static PsiExpression getRightOperands(PsiPolyadicExpression andChain, PsiExpression operand) {
    PsiExpression rOperands;
    if (operand == ArrayUtil.getLastElement(andChain.getOperands())) {
      rOperands = PsiUtil.skipParenthesizedExprDown(operand);
    }
    else {
      rOperands = SplitConditionUtil.getROperands(andChain, andChain.getTokenBeforeOperand(operand));
      // To preserve mark
      ((PsiPolyadicExpression)rOperands).getOperands()[0].replace(operand);
    }
    return rOperands;
  }

  @Nullable
  private static PsiPolyadicExpression findSurroundingConditionChain(@NotNull PsiExpression expression) {
    PsiExpression current = expression;
    PsiPolyadicExpression polyadicExpression;
    do {
      current = polyadicExpression = PsiTreeUtil.getParentOfType(current, PsiPolyadicExpression.class, true,
                                                                 PsiStatement.class, PsiLambdaExpression.class);
    }
    while (polyadicExpression != null && ((polyadicExpression.getOperationTokenType() != JavaTokenType.ANDAND &&
                                           polyadicExpression.getOperationTokenType() != JavaTokenType.OROR) ||
                                          PsiTreeUtil.isAncestor(polyadicExpression.getOperands()[0], expression, false)));
    return polyadicExpression;
  }

  @Nullable
  private static PsiConditionalExpression findSurroundingTernary(@NotNull PsiExpression expression) {
    PsiExpression current = expression;
    PsiConditionalExpression ternary;
    do {
      current = ternary = PsiTreeUtil.getParentOfType(current, PsiConditionalExpression.class, true,
                                                      PsiStatement.class, PsiLambdaExpression.class);
    }
    while (ternary != null && PsiTreeUtil.isAncestor(ternary.getCondition(), expression, false));
    return ternary;
  }

  private static boolean hasNameCollision(PsiElement declaration, PsiElement context) {
    if (declaration instanceof PsiDeclarationStatement) {
      PsiResolveHelper helper = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper();
      return StreamEx.of(((PsiDeclarationStatement)declaration).getDeclaredElements())
            .select(PsiLocalVariable.class)
            .map(PsiLocalVariable::getName)
            .nonNull()
            .anyMatch(name -> helper.resolveReferencedVariable(name, context) != null);
    }
    return false;
  }
}

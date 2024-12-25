// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.optionalToIf;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

interface Instruction {

  String generate();

  static @Nullable Instruction create(@NotNull PsiStatement statement) {
    PsiDeclarationStatement declaration = tryCast(statement, PsiDeclarationStatement.class);
    if (declaration != null) return Declaration.create(declaration);
    PsiReturnStatement returnStatement = tryCast(statement, PsiReturnStatement.class);
    if (returnStatement != null) return Return.create(returnStatement);
    PsiThrowStatement throwStatement = tryCast(statement, PsiThrowStatement.class);
    if (throwStatement != null) return Throw.create(throwStatement);
    PsiIfStatement ifStatement = tryCast(statement, PsiIfStatement.class);
    if (ifStatement != null) return Check.create(ifStatement);
    PsiBlockStatement block = tryCast(statement, PsiBlockStatement.class);
    if (block != null) return new CodeBlock(block);
    PsiExpressionStatement expressionStatement = tryCast(statement, PsiExpressionStatement.class);
    if (expressionStatement != null) {
      PsiAssignmentExpression assignment = tryCast(expressionStatement.getExpression(), PsiAssignmentExpression.class);
      if (assignment != null) return Assignment.create(assignment);
    }
    return new Statement(statement);
  }

  class CodeBlock implements Instruction {

    private final PsiBlockStatement myBlock;

    public CodeBlock(PsiBlockStatement block) {
      myBlock = block;
    }

    @Override
    public String generate() {
      return StringUtil.join(myBlock.getCodeBlock().getStatements(), s -> s.getText(), "\n");
    }
  }

  class Statement implements Instruction {

    private final PsiStatement myStatement;

    @Contract(pure = true)
    public Statement(@NotNull PsiStatement statement) {
      myStatement = statement;
    }

    @Override
    public String generate() {
      return myStatement.getText();
    }
  }

  class Assignment implements Instruction {

    final PsiVariable myLhs;
    final PsiExpression myRhs;

    @Contract(pure = true)
    public Assignment(@NotNull PsiVariable lhs, @NotNull PsiExpression rhs) {
      myLhs = lhs;
      myRhs = rhs;
    }

    @Override
    public String generate() {
      return myLhs.getName() + "=" + myRhs.getText() + ";\n";
    }

    static @Nullable Assignment create(@NotNull PsiAssignmentExpression assignment) {
      PsiExpression rhs = assignment.getRExpression();
      if (rhs == null) return null;
      PsiReference reference = tryCast(assignment.getLExpression(), PsiReference.class);
      if (reference == null) return null;
      PsiVariable lhs = tryCast(reference.resolve(), PsiVariable.class);
      return lhs == null ? null : new Assignment(lhs, rhs);
    }
  }

  class Declaration implements Instruction {

    final PsiVariable myLhs;
    final PsiExpression myRhs;

    @Contract(pure = true)
    public Declaration(PsiVariable lhs, PsiExpression rhs) {
      myLhs = lhs;
      myRhs = rhs;
    }

    @Override
    public String generate() {
      if (myLhs.getInitializer() == myRhs) return myLhs.getText();
      PsiVariable copy = (PsiVariable)myLhs.copy();
      copy.setInitializer(myRhs);
      return copy.getText();
    }

    static @Nullable Declaration create(@NotNull PsiDeclarationStatement declaration) {
      PsiElement[] declared = declaration.getDeclaredElements();
      if (declared.length != 1) return null;
      PsiVariable lhs = tryCast(declared[0], PsiVariable.class);
      if (lhs == null) return null;
      PsiExpression rhs = lhs.getInitializer();
      return rhs == null ? null : new Declaration(lhs, rhs);
    }
  }

  class Check implements Instruction {

    final PsiExpression myCondition;
    List<Instruction> myInstructions;
    private final List<Instruction> myElseInstructions;

    @Contract(pure = true)
    public Check(PsiExpression condition, List<Instruction> instructions, @Nullable List<Instruction> elseInstructions) {
      myCondition = condition;
      myInstructions = instructions;
      myElseInstructions = elseInstructions;
    }

    public boolean hasElseBranch() {
      return myElseInstructions != null;
    }

    @Override
    public String generate() {
      if (myInstructions.size() == 1 && !hasElseBranch()) {
        Instruction instruction = myInstructions.get(0);
        if (!(instruction instanceof Declaration) &&
            (!(instruction instanceof CodeBlock) || ((CodeBlock)instruction).myBlock.getCodeBlock().getStatements().length == 1)) {
          return "if(" + myCondition.getText() + ")" + instruction.generate();
        }
      }
      String thenBranch = "if(" + myCondition.getText() + "){\n" +
                          StringUtil.join(myInstructions, i -> i.generate(), "") +
                          "}\n";
      if (myElseInstructions == null) return thenBranch;
      return thenBranch +
             "else{\n" + StringUtil.join(myElseInstructions, i -> i.generate(), "") + "\n}";
    }

    static @Nullable Check create(@NotNull PsiIfStatement ifStatement) {
      PsiExpression condition = ifStatement.getCondition();
      if (condition == null) return null;
      PsiStatement thenBranch = ifStatement.getThenBranch();
      if (thenBranch == null) return null;
      List<Instruction> instructions = OptionalToIfInspection.createInstructions(ControlFlowUtils.unwrapBlock(thenBranch));
      if (instructions == null) return null;
      PsiStatement elseBranch = ifStatement.getElseBranch();
      List<Instruction> elseInstructions = null;
      if (elseBranch != null) {
        elseInstructions = OptionalToIfInspection.createInstructions(ControlFlowUtils.unwrapBlock(elseBranch));
        if (elseInstructions == null) return null;
      }
      return new Check(condition, instructions, elseInstructions);
    }
  }

  class Return implements Instruction {

    final PsiExpression myExpression;

    @Contract(pure = true)
    public Return(@NotNull PsiExpression expression) {
      myExpression = expression;
    }

    @Override
    public String generate() {
      return "return " + myExpression.getText() + ";\n";
    }

    static @Nullable Return create(@NotNull PsiReturnStatement returnStatement) {
      PsiExpression returnValue = returnStatement.getReturnValue();
      return returnValue == null ? null : new Return(returnValue);
    }
  }

  class Throw implements Instruction {

    final PsiExpression myException;

    @Contract(pure = true)
    public Throw(@NotNull PsiExpression exception) {myException = exception;}

    @Override
    public String generate() {
      return "throw " + myException.getText() + ";\n";
    }

    static @Nullable Throw create(@NotNull PsiThrowStatement throwStatement) {
      PsiExpression exception = throwStatement.getException();
      return exception == null ? null : new Throw(exception);
    }
  }
}

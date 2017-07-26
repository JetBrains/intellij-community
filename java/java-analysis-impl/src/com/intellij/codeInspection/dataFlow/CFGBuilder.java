/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.inliner.CallInliner;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightVariableBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A facade for building control flow graph used by {@link CallInliner} implementations
 */
public class CFGBuilder {
  private final ControlFlowAnalyzer myAnalyzer;
  private final Deque<JumpInstruction> myBranches = new ArrayDeque<>();

  CFGBuilder(ControlFlowAnalyzer analyzer) {
    myAnalyzer = analyzer;
  }

  public CFGBuilder pushUnknown() {
    myAnalyzer.pushUnknown();
    return this;
  }

  public CFGBuilder pushNull() {
    myAnalyzer.addInstruction(new PushInstruction(getFactory().getConstFactory().getNull(), null));
    return this;
  }

  public CFGBuilder pushExpression(PsiExpression expression) {
    expression.accept(myAnalyzer);
    return this;
  }

  public CFGBuilder pushVariable(PsiVariable variable) {
    myAnalyzer.addInstruction(
      new PushInstruction(getFactory().getVarFactory().createVariableValue(variable, false), null, true));
    return this;
  }

  public CFGBuilder push(DfaValue value) {
    myAnalyzer.addInstruction(new PushInstruction(value, null));
    return this;
  }

  public CFGBuilder pop() {
    myAnalyzer.addInstruction(new PopInstruction());
    return this;
  }

  public CFGBuilder dup() {
    myAnalyzer.addInstruction(new DupInstruction());
    return this;
  }

  public CFGBuilder dereferenceCheck(PsiReferenceExpression referenceExpression) {
    if (referenceExpression != null) {
      myAnalyzer.addInstruction(new DupInstruction());
      myAnalyzer.addInstruction(new FieldReferenceInstruction(referenceExpression, null));
    }
    return this;
  }

  public CFGBuilder splice(int count, int... replacement) {
    myAnalyzer.addInstruction(new SpliceInstruction(count, replacement));
    return this;
  }

  public CFGBuilder swap() {
    myAnalyzer.addInstruction(new SwapInstruction());
    return this;
  }

  public CFGBuilder invoke(PsiMethodCallExpression call) {
    myAnalyzer.addBareCall(call);
    return this;
  }

  public CFGBuilder ifConditionIs(boolean value) {
    ConditionalGotoInstruction gotoInstruction = new ConditionalGotoInstruction(null, value, null);
    myBranches.add(gotoInstruction);
    myAnalyzer.addInstruction(gotoInstruction);
    return this;
  }

  public CFGBuilder endIf() {
    myBranches.removeLast().setOffset(myAnalyzer.getInstructionCount());
    return this;
  }

  private CFGBuilder compare(IElementType relation) {
    myAnalyzer.addInstruction(new BinopInstruction(relation, null, myAnalyzer.getContext().getProject()));
    return this;
  }

  public CFGBuilder elseBranch() {
    GotoInstruction gotoInstruction = new GotoInstruction(null);
    myAnalyzer.addInstruction(gotoInstruction);
    endIf();
    myBranches.add(gotoInstruction);
    return this;
  }

  public CFGBuilder ifCondition(IElementType relation) {
    return compare(relation).ifConditionIs(true);
  }

  public CFGBuilder ifNotNull() {
    return pushNull().ifCondition(JavaTokenType.NE);
  }

  public CFGBuilder ifNull() {
    return pushNull().ifCondition(JavaTokenType.EQEQ);
  }

  public CFGBuilder boxUnbox(PsiExpression expression, PsiType expectedType) {
    myAnalyzer.generateBoxingUnboxingInstructionFor(expression, expectedType);
    return this;
  }

  public CFGBuilder checkNotNull(PsiExpression expression) {
    myAnalyzer.addInstruction(new CheckNotNullInstruction(expression));
    return this;
  }

  public CFGBuilder assign() {
    myAnalyzer.addInstruction(new AssignInstruction(null, null));
    return this;
  }

  public CFGBuilder assignTo(PsiVariable var) {
    return pushVariable(var).swap().assign();
  }

  public DfaValueFactory getFactory() {
    return myAnalyzer.getFactory();
  }

  /**
   * Generates instructions to invoke functional expression (inlining it if possible) which
   * consumes given amount of stack arguments
   *
   * @param argCount             number of stack arguments to consume
   * @param functionalExpression a functional expression to invoke
   * @return this builder
   */
  public CFGBuilder invokeFunction(int argCount, @Nullable PsiExpression functionalExpression) {
    PsiExpression stripped = PsiUtil.deparenthesizeExpression(functionalExpression);
    if (stripped instanceof PsiLambdaExpression) {
      PsiLambdaExpression lambda = (PsiLambdaExpression)stripped;
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      if (parameters.length == argCount && lambda.getBody() != null) {
        StreamEx.ofReversed(parameters).forEach(p -> assignTo(p).pop());
        return inlineLambda(lambda);
      }
    }
    if (stripped instanceof PsiMethodReferenceExpression) {
      PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)stripped;
      JavaResolveResult resolveResult = methodRef.advancedResolve(false);
      PsiMethod method = ObjectUtils.tryCast(resolveResult.getElement(), PsiMethod.class);
      if (method != null) {
        // TODO: advanced method references support, including contracts
        splice(argCount);
        pushExpression(methodRef);
        pop();
        PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        PsiType returnType = substitutor.substitute(method.getReturnType());
        if (returnType != null) {
          push(getFactory().createTypeValue(returnType, DfaPsiUtil.getElementNullability(returnType, method)));
          myAnalyzer.generateBoxingUnboxingInstructionFor(methodRef, returnType, LambdaUtil.getFunctionalInterfaceReturnType(methodRef));
        }
        else {
          pushUnknown();
        }
        return this;
      }
    }
    splice(argCount);
    if (functionalExpression == null) {
      pushUnknown();
      return this;
    }
    pushExpression(functionalExpression);
    checkNotNull(functionalExpression);
    pop();
    PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalExpression.getType());
    if (returnType != null) {
      push(getFactory().createTypeValue(returnType, DfaPsiUtil.getTypeNullability(returnType)));
    }
    else {
      pushUnknown();
    }
    return this;
  }

  public CFGBuilder inlineLambda(PsiLambdaExpression lambda) {
    myAnalyzer.inlineLambda(lambda);
    return this;
  }

  public PsiVariable createTempVariable(PsiType type) {
    return new LightVariableBuilder<>("tmp$" + myAnalyzer.getInstructionCount(), type, myAnalyzer.getContext());
  }
}

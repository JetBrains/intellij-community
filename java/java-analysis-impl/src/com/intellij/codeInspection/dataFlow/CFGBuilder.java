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
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightVariableBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A facade for building control flow graph used by {@link CallInliner} implementations
 */
@SuppressWarnings("UnusedReturnValue")
public class CFGBuilder {
  private final ControlFlowAnalyzer myAnalyzer;
  private final Deque<JumpInstruction> myBranches = new ArrayDeque<>();
  private final Map<PsiExpression, PsiVariable> myMethodRefQualifiers = new HashMap<>();

  CFGBuilder(ControlFlowAnalyzer analyzer) {
    myAnalyzer = analyzer;
  }

  /**
   * Generate instructions to push unknown DfaValue on stack.
   * <p>
   * Stack before: ...
   * <p>
   * Stack after: ... unknown
   *
   * @return this builder
   */
  public CFGBuilder pushUnknown() {
    myAnalyzer.pushUnknown();
    return this;
  }

  /**
   * Generate instructions to push null DfaValue on stack.
   * <p>
   * Stack before: ...
   * <p>
   * Stack after: ... null
   *
   * @return this builder
   */
  public CFGBuilder pushNull() {
    myAnalyzer.addInstruction(new PushInstruction(getFactory().getConstFactory().getNull(), null));
    return this;
  }

  /**
   * Generate instructions to evaluate given expression and push its result on stack.
   * <p>
   * Stack before: ...
   * <p>
   * Stack after: ... expression_result
   *
   * @param expression expression to evaluate
   * @return this builder
   */
  public CFGBuilder pushExpression(PsiExpression expression) {
    expression.accept(myAnalyzer);
    return this;
  }

  /**
   * Generate instructions to push given variable on stack for subsequent write.
   * <p>
   * Stack before: ...
   * <p>
   * Stack after: ... variable
   *
   * @param variable to push
   * @return this builder
   */
  public CFGBuilder pushVariable(PsiVariable variable) {
    myAnalyzer.addInstruction(
      new PushInstruction(getFactory().getVarFactory().createVariableValue(variable, false), null, true));
    return this;
  }

  /**
   * Generate instructions to push given DfaValue on stack.
   * <p>
   * Stack before: ...
   * <p>
   * Stack after: ... value
   *
   * @param value value to push
   * @return this builder
   */
  public CFGBuilder push(DfaValue value) {
    myAnalyzer.addInstruction(new PushInstruction(value, null));
    return this;
  }

  /**
   * Generate instructions to pop single DfaValue from stack
   * <p>
   * Stack before: ... value
   * <p>
   * Stack after: ...
   *
   * @return this builder
   */
  public CFGBuilder pop() {
    myAnalyzer.addInstruction(new PopInstruction());
    return this;
  }

  /**
   * Generate instructions to duplicate top stack value
   * <p>
   * Stack before: ... value
   * <p>
   * Stack after: ... value value
   *
   * @return this builder
   */
  public CFGBuilder dup() {
    myAnalyzer.addInstruction(new DupInstruction());
    return this;
  }

  /**
   * Generate instructions to dereference check for the top stack value.
   * Stack is unchanged.
   *
   * @param referenceExpression a PSI anchor to report possible NPE if top stack value is nullable
   * @return this builder
   */
  public CFGBuilder dereferenceCheck(PsiReferenceExpression referenceExpression) {
    if (referenceExpression != null) {
      myAnalyzer.addInstruction(new DupInstruction());
      myAnalyzer.addInstruction(new FieldReferenceInstruction(referenceExpression, null));
    }
    return this;
  }

  /**
   * Generate instructions to pop given number of stack values, then push some or all of popped values referred by indices,
   * possibly duplicating them
   * <p>
   * E.g. {@code splice(2, 0, 1, 0)} will change "... val1 val2" stack to "... val2 val1 val2".
   * Stack depth is increased by {@code replacement.length - count}.
   *
   * @param count       number of values to pop
   * @param replacement replacement indices from 0 to {@code count-1}. Index 0 = top stack value, index 1 = next value and so on.
   * @return this builder
   */
  public CFGBuilder splice(int count, int... replacement) {
    myAnalyzer.addInstruction(new SpliceInstruction(count, replacement));
    return this;
  }

  /**
   * Generate instructions to swap two top stack values
   * <p>
   * Stack before: ... val1 val2
   * <p>
   * Stack after: ... val2 val1
   *
   * @return this builder
   */
  public CFGBuilder swap() {
    myAnalyzer.addInstruction(new SwapInstruction());
    return this;
  }

  /**
   * Generate instructions to invoke the method associated with given method call assuming that method arguments and qualifier
   * are already on stack. If vararg call is specified, vararg arguments should be placed as is, without packing into array,
   * so number of arguments may differ from number of method parameters.
   * <p>
   * Stack before: ... qualifier arg1 arg2 ... argN
   * <p>
   * Stack after: ... return value
   * <p>
   * Note that qualifier must be present even if method is static (use {@link #pushUnknown()}). Similarly, return value will be pushed
   * on stack always, even if method is void.
   *
   * @param call a method call to generate invocation upon
   * @return this builder
   */
  public CFGBuilder invoke(PsiMethodCallExpression call) {
    myAnalyzer.addBareCall(call, call.getMethodExpression());
    return this;
  }

  /**
   * Generate instructions to compare two values on top of stack with given relation operation (e.g. {@link JavaTokenType#GT}).
   * <p>
   * Stack before: ... val1 val2
   * <p>
   * Stack after: ... result_of_val1_relation_val2
   *
   * @param relation relation to use for comparison
   * @return this builder
   */
  private CFGBuilder compare(IElementType relation) {
    myAnalyzer.addInstruction(new BinopInstruction(relation, null, myAnalyzer.getContext().getProject()));
    return this;
  }

  /**
   * Generate instructions to start a conditional block based on stack top value, consuming this value
   * <p>
   * Stack before: ... condition
   * <p>
   * Stack after: ...
   * <p>
   * The conditional block must end with {@link #endIf()} and may contain one {@link #elseBranch()} inside.
   * Nested conditional blocks are acceptable.
   *
   * @param value a value condition must have to visit conditional block
   * @return this builder
   */
  public CFGBuilder ifConditionIs(boolean value) {
    ConditionalGotoInstruction gotoInstruction = new ConditionalGotoInstruction(null, value, null);
    myBranches.add(gotoInstruction);
    myAnalyzer.addInstruction(gotoInstruction);
    return this;
  }

  /**
   * Generate instructions to start a conditional block based on result of comparison of
   * two stack values with given relation (e.g. {@link JavaTokenType#GT}), consuming these values.
   * <p>
   * Stack before: ... val1 val2
   * <p>
   * Stack after: ...
   * <p>
   * The conditional block must end with {@link #endIf()} and may contain one {@link #elseBranch()} inside.
   * Nested conditional blocks are acceptable.
   *
   * @param relation a relation to use to compare two stack values. Conditional block will be executed if "val1 relation val2" is true.
   * @return this builder
   */
  public CFGBuilder ifCondition(IElementType relation) {
    return compare(relation).ifConditionIs(true);
  }

  /**
   * Generate instructions to start a conditional block which is executed if top stack value is not null.
   * <p>
   * Stack before: ... value
   * <p>
   * Stack after: ...
   * <p>
   * The conditional block must end with {@link #endIf()} and may contain one {@link #elseBranch()} inside.
   * Nested conditional blocks are acceptable.
   *
   * @return this builder
   */
  public CFGBuilder ifNotNull() {
    return pushNull().ifCondition(JavaTokenType.NE);
  }

  /**
   * Generate instructions to start a conditional block which is executed if top stack value is null.
   * <p>
   * Stack before: ... value
   * <p>
   * Stack after: ...
   * <p>
   * The conditional block must end with {@link #endIf()} and may contain one {@link #elseBranch()} inside.
   * Nested conditional blocks are acceptable.
   *
   * @return this builder
   */
  public CFGBuilder ifNull() {
    return pushNull().ifCondition(JavaTokenType.EQEQ);
  }

  /**
   * Generate instructions to finish a conditional block started with {@link #ifCondition(IElementType)}, {@link #ifConditionIs(boolean)},
   * {@link #ifNull()} or {@link #ifNotNull()}. Stack is unchanged.
   *
   * @return this builder
   */
  public CFGBuilder endIf() {
    myBranches.removeLast().setOffset(myAnalyzer.getInstructionCount());
    return this;
  }

  /**
   * Generate instructions to finish a "then-branch" and start an "else-branch" of a conditional block started
   * with {@link #ifCondition(IElementType)}, {@link #ifConditionIs(boolean)}, {@link #ifNull()} or {@link #ifNotNull()}.
   * Stack is unchanged.
   *
   * @return this builder
   */
  public CFGBuilder elseBranch() {
    GotoInstruction gotoInstruction = new GotoInstruction(null);
    myAnalyzer.addInstruction(gotoInstruction);
    endIf();
    myBranches.add(gotoInstruction);
    return this;
  }

  /**
   * Generate instructions to start a loop. Stack is unchanged. Loop must be terminated via {@link #endWhileUnknown()}.
   * Nested loops are acceptable.
   *
   * @return this builder
   */
  public CFGBuilder doWhile() {
    ConditionalGotoInstruction jump = new ConditionalGotoInstruction(null, false, null);
    jump.setOffset(myAnalyzer.getInstructionCount());
    myBranches.add(jump);
    return this;
  }

  /**
   * Generate instructions to end a loop started via {@link #doWhile()} by unknown condition. Stack is unchanged.
   *
   * @return this builder
   */
  public CFGBuilder endWhileUnknown() {
    pushUnknown();
    myAnalyzer.addInstruction((ConditionalGotoInstruction)myBranches.removeLast());
    return this;
  }

  /**
   * Generate instructions to box or unbox stack top value if necessary to satisfy the specified expected type.
   * <p>
   * Stack before: ... value
   * <p>
   * Stack after: ... boxed_or_unboxed_value
   *
   * @param expression an expression which result is placed on the top of stack
   * @param expectedType an expected type
   *
   * @return this builder
   */
  public CFGBuilder boxUnbox(PsiExpression expression, PsiType expectedType) {
    myAnalyzer.generateBoxingUnboxingInstructionFor(expression, expectedType);
    return this;
  }

  /**
   * Generate instructions to box or unbox stack top value if necessary to satisfy the specified expected type.
   * <p>
   * Stack before: ... value
   * <p>
   * Stack after: ... boxed_or_unboxed_value
   *
   * @param expression an expression which is used to anchor instructions so issued warnings can point to this expression
   * @param expressionType an actual type of the expression on top of stack
   * @param expectedType an expected type
   *
   * @return this builder
   */
  public CFGBuilder boxUnbox(PsiExpression expression, PsiType expressionType, PsiType expectedType) {
    myAnalyzer.generateBoxingUnboxingInstructionFor(expression, expressionType, expectedType);
    return this;
  }

  /**
   * Generate instructions to flush known values of non-final fields of mutable classes.
   *
   * @return this builder
   */
  public CFGBuilder flushFields() {
    myAnalyzer.addInstruction(new FlushVariableInstruction(null));
    return this;
  }

  /**
   * Generate instructions to check that stack top value is not null issuing a warning like "argument is nullable" if
   * this is not satisfied. Stack is unchanged.
   *
   * @param expression an anchor expression to bind a warning to
   * @return this builder
   */
  public CFGBuilder checkNotNull(PsiExpression expression) {
    myAnalyzer.addInstruction(new CheckNotNullInstruction(expression));
    return this;
  }

  /**
   * Generate instructions to assign top stack value to the second stack value
   * (usually pushed via {@link #pushVariable(PsiVariable)}).
   * <p>
   * Stack before: ... variable_for_write value
   * <p>
   * Stack after: ... variable
   *
   * @return this builder
   */
  public CFGBuilder assign() {
    myAnalyzer.addInstruction(new AssignInstruction(null, null));
    return this;
  }

  /**
   * Generate instructions to assign top stack value to the specified variable
   * <p>
   * Stack before: ... value
   * <p>
   * Stack after: ... variable
   *
   * @return this builder
   */
  public CFGBuilder assignTo(PsiVariable var) {
    return pushVariable(var).swap().assign();
  }

  /**
   * Returns a {@link DfaValueFactory} associated with current control flow.
   *
   * @return a {@link DfaValueFactory} associated with current control flow.
   */
  public DfaValueFactory getFactory() {
    return myAnalyzer.getFactory();
  }

  /**
   * Generate instructions to evaluate functional expression (but not invoke the function itself
   * -- see {@link #invokeFunction(int, PsiExpression)}). Stack is unchanged.
   *
   * @param functionalExpression a functional expression to evaluate
   * @return this builder
   */
  public CFGBuilder evaluateFunction(@Nullable PsiExpression functionalExpression) {
    PsiExpression stripped = PsiUtil.deparenthesizeExpression(functionalExpression);
    if (stripped == null || stripped instanceof PsiLambdaExpression) {
      return this;
    }
    if (stripped instanceof PsiMethodReferenceExpression) {
      PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)stripped;
      PsiExpression qualifier = methodRef.getQualifierExpression();
      if (qualifier != null && !PsiMethodReferenceUtil.isStaticallyReferenced(methodRef)) {
        PsiVariable qualifierBinding = createTempVariable(qualifier.getType());
        pushVariable(qualifierBinding)
          .pushExpression(qualifier)
          .dup();
        myAnalyzer.addInstruction(new FieldReferenceInstruction(qualifier, ControlFlowAnalyzer.METHOD_REFERENCE_QUALIFIER_SYNTHETIC_FIELD));
        assign().pop();
        myMethodRefQualifiers.put(methodRef, qualifierBinding);
      } else {
        pushExpression(methodRef).pop();
      }
      return this;
    }
    return pushExpression(functionalExpression)
      .checkNotNull(functionalExpression)
      .pop();
  }

  /**
   * Generates instructions to invoke functional expression (inlining it if possible) which
   * consumes given amount of stack arguments, assuming that it was previously evaluated
   * (see {@link #evaluateFunction(PsiExpression)}).
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
      if (method != null && !method.isVarArgs()) {
        int expectedArgCount = method.getParameterList().getParametersCount();
        boolean pushQualifier = true;
        if (!method.hasModifierProperty(PsiModifier.STATIC) && !method.isConstructor()) {
          pushQualifier = !PsiMethodReferenceUtil.isStaticallyReferenced(methodRef);
          if (!pushQualifier) {
            expectedArgCount++; // qualifier is already on stack for statically referenced method ref
          }
        }
        if (argCount == expectedArgCount) {
          if (pushQualifier) {
            PsiVariable qualifierVar = myMethodRefQualifiers.remove(methodRef);
            DfaValue qualifierValue = qualifierVar == null ? DfaUnknownValue.getInstance() :
                                      getFactory().getVarFactory().createVariableValue(qualifierVar, false);
            push(qualifierValue);
            if (argCount > 0) {
              // reorder stack to put qualifier before args (.. arg1 arg2 arg3 qualifier => .. qualifier arg1 arg2 arg3)
              int[] permutation = new int[argCount + 1];
              for (int i = 1; i < permutation.length; i++) {
                permutation[i] = argCount + 1 - i;
              }
              splice(argCount + 1, permutation);
            }
          }
          myAnalyzer.addBareCall(null, methodRef);
          myAnalyzer.generateBoxingUnboxingInstructionFor(methodRef, resolveResult.getSubstitutor().substitute(method.getReturnType()),
                                                          LambdaUtil.getFunctionalInterfaceReturnType(methodRef));
          return this;
        }
      }
      PsiElement qualifier = methodRef.getQualifier();
      if(qualifier instanceof PsiTypeElement && ((PsiTypeElement)qualifier).getType() instanceof PsiArrayType) {
        // like String[]::new
        splice(argCount)
          .push(getFactory().createTypeValue(((PsiTypeElement)qualifier).getType(), Nullness.NOT_NULL));
        return this;
      }
    }
    splice(argCount);
    if (functionalExpression == null) {
      pushUnknown();
      return this;
    }
    // Unknown function
    flushFields();
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

  /**
   * Create a temporary {@link PsiVariable} (not declared in the original code) to be used within this control flow.
   *
   * @param type a type of variable to create
   * @return newly created variable
   */
  @NotNull
  public PsiVariable createTempVariable(@Nullable PsiType type) {
    if(type == null) {
      type = PsiType.VOID;
    }
    return new LightVariableBuilder<>("tmp$" + myAnalyzer.getInstructionCount(), type, myAnalyzer.getContext());
  }

  /**
   * A convenient method to chain specific builder operation
   *
   * @param operation to execute on this builder
   * @return this builder
   */
  public CFGBuilder chain(Consumer<CFGBuilder> operation) {
    operation.accept(this);
    return this;
  }
}

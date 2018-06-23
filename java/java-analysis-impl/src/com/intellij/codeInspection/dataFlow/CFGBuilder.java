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

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.inliner.CallInliner;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import one.util.streamex.IntStreamEx;
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
  private final Deque<Runnable> myBranches = new ArrayDeque<>();
  private final Map<PsiExpression, DfaVariableValue> myMethodRefQualifiers = new HashMap<>();

  CFGBuilder(ControlFlowAnalyzer analyzer) {
    myAnalyzer = analyzer;
  }

  private CFGBuilder add(Instruction instruction) {
    myAnalyzer.addInstruction(instruction);
    return this;
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
    return add(new PushInstruction(getFactory().getConstFactory().getNull(), null));
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
   * Generate instructions to push given variable value on stack for subsequent write.
   * <p>
   * Stack before: ...
   * <p>
   * Stack after: ... variable
   *
   * @param variable to push
   * @return this builder
   */
  public CFGBuilder pushForWrite(DfaVariableValue variable) {
    return add(new PushInstruction(variable, null, true));
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
    return add(new PushInstruction(value, null));
  }

  /**
   * Generate instructions to push given DfaValue on stack and bind it to given expression.
   * <p>
   * Stack before: ...
   * <p>
   * Stack after: ... value
   *
   * @param value value to push
   * @param expression expression which result is being pushed
   * @return this builder
   */
  public CFGBuilder push(DfaValue value, PsiExpression expression) {
    return add(new PushInstruction(value, expression));
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
    return add(new PopInstruction());
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
    return add(new DupInstruction());
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
    return add(new SpliceInstruction(count, replacement));
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
    return add(new SwapInstruction());
  }

  /**
   * Generate instructions to replace class value on top of stack with the corresponding object value
   * <p>
   * Stack before: ... class_value
   * <p>
   * Stack after: ... object_value
   *
   * @return this builder
   */
  public CFGBuilder objectOf() {
    return add(new ObjectOfInstruction());
  }

  /**
   * Generate instructions to perform an Class.isInstance operation
   * <p>
   * Stack before: ... object class_object
   * <p>
   * Stack after: ... result
   *
   * @param anchor element to bind this instruction to
   * @return this builder
   */
  public CFGBuilder isInstance(PsiMethodCallExpression anchor) {
    return add(new InstanceofInstruction(anchor));
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
  CFGBuilder compare(IElementType relation) {
    return add(new BinopInstruction(relation, null, PsiType.BOOLEAN));
  }

  /**
   * Generate instructions to start a conditional block based on stack top value, consuming this value
   * <p>
   * Stack before: ... condition
   * <p>
   * Stack after: ...
   * <p>
   * The conditional block must end with {@link #end()} and may contain one {@link #elseBranch()} inside.
   * Nested conditional blocks are acceptable.
   *
   * @param value a value condition must have to visit conditional block
   * @return this builder
   */
  public CFGBuilder ifConditionIs(boolean value) {
    ConditionalGotoInstruction gotoInstruction = new ConditionalGotoInstruction(null, value, null);
    myBranches.add(() -> gotoInstruction.setOffset(myAnalyzer.getInstructionCount()));
    return add(gotoInstruction);
  }

  /**
   * Generate instructions to start a conditional block based on result of comparison of
   * two stack values with given relation (e.g. {@link JavaTokenType#GT}), consuming these values.
   * <p>
   * Stack before: ... val1 val2
   * <p>
   * Stack after: ...
   * <p>
   * The conditional block must end with {@link #end()} and may contain one {@link #elseBranch()} inside.
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
   * The conditional block must end with {@link #end()} and may contain one {@link #elseBranch()} inside.
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
   * The conditional block must end with {@link #end()} and may contain one {@link #elseBranch()} inside.
   * Nested conditional blocks are acceptable.
   *
   * @return this builder
   */
  public CFGBuilder ifNull() {
    return pushNull().ifCondition(JavaTokenType.EQEQ);
  }

  /**
   * Generate instructions to finish a conditional block or a loop. Stack is unchanged.
   *
   * @return this builder
   */
  public CFGBuilder end() {
    myBranches.removeLast().run();
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
    add(gotoInstruction).end();
    myBranches.add(() -> gotoInstruction.setOffset(myAnalyzer.getInstructionCount()));
    return this;
  }

  /**
   * Generate instructions to start a loop. Stack is unchanged. Loop must be terminated via {@link #end()}.
   * Nested loops are acceptable.
   *
   * @return this builder
   */
  public CFGBuilder doWhileUnknown() {
    ConditionalGotoInstruction jump = new ConditionalGotoInstruction(null, false, null);
    jump.setOffset(myAnalyzer.getInstructionCount());
    myBranches.add(() -> pushUnknown().add(jump));
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
    return add(new FlushFieldsInstruction());
  }

  /**
   * Generate instructions to check that stack top value is not null issuing a warning like "argument is nullable" if
   * this is not satisfied. Stack is unchanged.
   *
   * @param expression an anchor expression to bind a warning to
   * @param kind a type of nullability problem to report if value is nullable
   * @return this builder
   */
  public <T extends PsiElement> CFGBuilder checkNotNull(T expression, NullabilityProblemKind<T> kind) {
    return add(new CheckNotNullInstruction(kind.problem(expression)));
  }

  /**
   * Generate instructions to assign top stack value to the second stack value
   * (usually pushed via {@link #pushForWrite(DfaVariableValue)}).
   * <p>
   * Stack before: ... variable_for_write value
   * <p>
   * Stack after: ... variable
   *
   * @return this builder
   */
  public CFGBuilder assign() {
    return add(new AssignInstruction(null, null));
  }

  /**
   * Generate instructions to assign given source value to the given target value. Stack remains unchanged.
   * May skip generating instructions if target is not writable (e.g. not a variable)
   *
   * @param target target to write
   * @param source source value
   * @return this builder
   */
  public CFGBuilder assignAndPop(DfaValue target, DfaValue source) {
    if (target instanceof DfaVariableValue) {
      if (source == DfaUnknownValue.getInstance()) {
        add(new FlushVariableInstruction((DfaVariableValue)target));
      } else {
        pushForWrite((DfaVariableValue)target).push(source).assign().pop();
      }
    }
    return this;
  }

  /**
   * Generate instructions to assign given source value to the given target value and leave the result on stack.
   * <p>
   * Stack before: ...
   * <p>
   * Stack after: ... target
   *
   * @param target target to write
   * @param source source value
   * @return this builder
   */
  public CFGBuilder assign(DfaValue target, DfaValue source) {
    if (target instanceof DfaVariableValue) {
      if (source == DfaUnknownValue.getInstance()) {
        add(new FlushVariableInstruction((DfaVariableValue)target)).push(target);
      } else {
        pushForWrite((DfaVariableValue)target).push(source).assign();
      }
    } else {
      push(source);
    }
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
    return pushForWrite(getFactory().getVarFactory().createVariableValue(var)).swap().assign();
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
  public CFGBuilder assignTo(DfaVariableValue var) {
    return pushForWrite(var).swap().assign();
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
        DfaVariableValue qualifierBinding = createTempVariable(qualifier.getType());
        pushForWrite(qualifierBinding)
          .pushExpression(qualifier)
          .checkNotNull(qualifier, NullabilityProblemKind.fieldAccessNPE)
          .assign()
          .pop();
        myMethodRefQualifiers.put(methodRef, qualifierBinding);
      }
      return this;
    }
    return pushExpression(functionalExpression)
      .checkNotNull(functionalExpression, NullabilityProblemKind.passingNullableToNotNullParameter)
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
    return invokeFunction(argCount, functionalExpression, Nullability.UNKNOWN);
  }

  /**
   * Generates instructions to invoke functional expression (inlining it if possible) which
   * consumes given amount of stack arguments, assuming that it was previously evaluated
   * (see {@link #evaluateFunction(PsiExpression)}).
   *
   * @param argCount             number of stack arguments to consume
   * @param functionalExpression a functional expression to invoke
   * @param resultNullability       an expected nullability of the lambda result
   * @return this builder
   */
  public CFGBuilder invokeFunction(int argCount, @Nullable PsiExpression functionalExpression, Nullability resultNullability) {
    PsiExpression stripped = PsiUtil.deparenthesizeExpression(functionalExpression);
    if (stripped instanceof PsiLambdaExpression) {
      PsiLambdaExpression lambda = (PsiLambdaExpression)stripped;
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      if (parameters.length == argCount && lambda.getBody() != null) {
        StreamEx.ofReversed(parameters).forEach(p -> assignTo(p).pop());
        return inlineLambda(lambda, resultNullability);
      }
    }
    if (stripped instanceof PsiMethodReferenceExpression) {
      PsiMethodReferenceExpression methodRef = (PsiMethodReferenceExpression)stripped;
      JavaResolveResult resolveResult = methodRef.advancedResolve(false);
      PsiMethod method = ObjectUtils.tryCast(resolveResult.getElement(), PsiMethod.class);
      if (method != null && !method.isVarArgs()) {
        if (processKnownMethodReference(argCount, methodRef, method)) return this;
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
            DfaValue qualifierValue = myMethodRefQualifiers.remove(methodRef);
            push(qualifierValue == null ? DfaUnknownValue.getInstance() : qualifierValue);
            moveTopValue(argCount);
          }
          myAnalyzer.addBareCall(null, methodRef);
          myAnalyzer.generateBoxingUnboxingInstructionFor(methodRef, resolveResult.getSubstitutor().substitute(method.getReturnType()),
                                                          LambdaUtil.getFunctionalInterfaceReturnType(methodRef));
          if (resultNullability == Nullability.NOT_NULL) {
            checkNotNull(methodRef, NullabilityProblemKind.nullableFunctionReturn);
          }
          return this;
        }
      }
      PsiElement qualifier = methodRef.getQualifier();
      if(qualifier instanceof PsiTypeElement && ((PsiTypeElement)qualifier).getType() instanceof PsiArrayType) {
        // like String[]::new
        splice(argCount)
          .push(getFactory().createTypeValue(((PsiTypeElement)qualifier).getType(), Nullability.NOT_NULL));
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

  private boolean processKnownMethodReference(int argCount, PsiMethodReferenceExpression methodRef, PsiMethod method) {
    if (argCount != 1 || !method.getName().equals("isInstance")) return false;
    PsiClassObjectAccessExpression qualifier = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(methodRef.getQualifierExpression()),
                                                                   PsiClassObjectAccessExpression.class);
    if (qualifier == null) return false;
    PsiType type = qualifier.getOperand().getType();
    push(getFactory().createTypeValue(type, Nullability.NOT_NULL));
    add(new InstanceofInstruction(methodRef, null, type));
    return true;
  }

  /**
   * Generate instructions to move top stack value to the specified depth
   * <p>
   * Stack before: ... val#1 val#2 ... val#depth topValue
   * <p>
   * Stack after: ... topValue val#1 val#2 ... val#depth
   *
   * @param depth a desired depth for the top stack value
   */
  private void moveTopValue(int depth) {
    if (depth > 0) {
      int[] permutation = new int[depth + 1];
      for (int i = 1; i < permutation.length; i++) {
        permutation[i] = depth + 1 - i;
      }
      splice(depth + 1, permutation);
    }
  }

  /**
   * Inlines given lambda. Lambda parameters are assumed to be assigned already (if necessary).
   * <p>
   * Stack before: ...
   * <p>
   * Stack after: ... lambdaResult
   *
   * @param lambda lambda to inline
   * @param resultNullability a required return value nullability
   * @return this builder
   */
  public CFGBuilder inlineLambda(PsiLambdaExpression lambda, Nullability resultNullability) {
    PsiElement body = lambda.getBody();
    PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(body);
    if (expression != null) {
      pushExpression(expression);
      boxUnbox(expression, LambdaUtil.getFunctionalInterfaceReturnType(lambda));
      if(resultNullability == Nullability.NOT_NULL) {
        checkNotNull(expression, NullabilityProblemKind.nullableFunctionReturn);
      }
    } else if(body instanceof PsiCodeBlock) {
      DfaVariableValue variable = createTempVariable(LambdaUtil.getFunctionalInterfaceReturnType(lambda));
      myAnalyzer.inlineBlock((PsiCodeBlock)body, resultNullability, variable);
      push(variable);
    } else {
      pushUnknown();
    }
    return this;
  }

  public CFGBuilder loopOver(PsiExpression[] expressions, DfaVariableValue targetVariable) {
    DfaValueFactory factory = getFactory();
    if (expressions.length > ControlFlowAnalyzer.MAX_UNROLL_SIZE) {
      for (PsiExpression expression : expressions) {
        pushExpression(expression);
        pop();
      }
      ConditionalGotoInstruction condGoto = new ConditionalGotoInstruction(null, false, null);
      condGoto.setOffset(myAnalyzer.getInstructionCount());
      myBranches.add(() -> pushUnknown().add(condGoto));
      assign(targetVariable, factory.createCommonValue(expressions));
    } else {
      push(factory.getConstFactory().getSentinel());
      for (PsiExpression expression : expressions) {
        pushExpression(expression);
      }
      // Revert order
      add(new SpliceInstruction(expressions.length, IntStreamEx.ofIndices(expressions).toArray()));
      GotoInstruction gotoInstruction = new GotoInstruction(null);
      gotoInstruction.setOffset(myAnalyzer.getInstructionCount());
      dup().push(factory.getConstFactory().getSentinel()).compare(JavaTokenType.EQEQ);
      ConditionalGotoInstruction condGoto = new ConditionalGotoInstruction(null, false, null);
      add(condGoto);
      assignTo(targetVariable);
      myBranches.add(() -> {
        add(gotoInstruction);
        condGoto.setOffset(myAnalyzer.getInstructionCount());
        pop();
      });
    }
    return this;
  }

  /**
   * Create a synthetic variable (not declared in the original code) to be used within this control flow.
   *
   * @param type a type of variable to create
   * @return newly created variable
   */
  @NotNull
  public DfaVariableValue createTempVariable(@Nullable PsiType type) {
    return myAnalyzer.createTempVariable(type);
  }

  /**
   * A convenient method to chain specific builder operation
   *
   * @param operation to execute on this builder
   * @return this builder
   */
  public CFGBuilder chain(Consumer<? super CFGBuilder> operation) {
    operation.accept(this);
    return this;
  }
}

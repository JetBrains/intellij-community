// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.intellij.java.analysis.JavaAnalysisBundle.BUNDLE;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION;
import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Represents a kind of nullability problem
 * @param <T> a type of anchor element which could be associated with given nullability problem kind
 */
public final class NullabilityProblemKind<T extends PsiElement> {
  private static final String NPE = JAVA_LANG_NULL_POINTER_EXCEPTION;
  private static final String RE = JAVA_LANG_RUNTIME_EXCEPTION;

  private final String myName;
  private final String myAlwaysNullMessage;
  private final String myNormalMessage;
  private final @Nullable String myException;

  private NullabilityProblemKind(@Nullable String exception, @NotNull String name) {
    myException = exception;
    myName = name;
    myAlwaysNullMessage = null;
    myNormalMessage = null;
  }

  private NullabilityProblemKind(@Nullable String exception, @NotNull String name,
                                 @NotNull @PropertyKey(resourceBundle = BUNDLE) String message) {
    this(exception, name, message, message);
  }

  private NullabilityProblemKind(@Nullable String exception, @NotNull String name,
                                 @NotNull @PropertyKey(resourceBundle = BUNDLE) String alwaysNullMessage,
                                 @NotNull @PropertyKey(resourceBundle = BUNDLE) String normalMessage) {
    myException = exception;
    myName = name;
    myAlwaysNullMessage = JavaAnalysisBundle.message(alwaysNullMessage);
    myNormalMessage = JavaAnalysisBundle.message(normalMessage);
  }

  public static final NullabilityProblemKind<PsiMethodCallExpression> callNPE =
    new NullabilityProblemKind<>(NPE, "callNPE", "dataflow.message.npe.method.invocation.sure", "dataflow.message.npe.method.invocation");
  public static final NullabilityProblemKind<PsiMethodReferenceExpression> callMethodRefNPE =
    new NullabilityProblemKind<>(NPE, "callMethodRefNPE", "dataflow.message.npe.methodref.invocation");
  public static final NullabilityProblemKind<PsiNewExpression> innerClassNPE =
    new NullabilityProblemKind<>(NPE, "innerClassNPE", "dataflow.message.npe.inner.class.construction.sure",
                                 "dataflow.message.npe.inner.class.construction");
  public static final NullabilityProblemKind<PsiExpression> fieldAccessNPE =
    new NullabilityProblemKind<>(NPE, "fieldAccessNPE", "dataflow.message.npe.field.access.sure", "dataflow.message.npe.field.access");
  public static final NullabilityProblemKind<PsiArrayAccessExpression> arrayAccessNPE =
    new NullabilityProblemKind<>(NPE, "arrayAccessNPE", "dataflow.message.npe.array.access.sure", "dataflow.message.npe.array.access");
  public static final NullabilityProblemKind<PsiElement> unboxingNullable =
    new NullabilityProblemKind<>(NPE, "unboxingNullable", "dataflow.message.unboxing");
  public static final NullabilityProblemKind<PsiExpression> assigningToNotNull =
    new NullabilityProblemKind<>(null, "assigningToNotNull", "dataflow.message.assigning.null", "dataflow.message.assigning.nullable");
  public static final NullabilityProblemKind<PsiExpression> assigningToNonAnnotatedField =
    new NullabilityProblemKind<>(null, "assigningToNonAnnotatedField", "dataflow.message.assigning.null.notannotated",
                                 "dataflow.message.assigning.nullable.notannotated");
  public static final NullabilityProblemKind<PsiExpression> storingToNotNullArray =
    new NullabilityProblemKind<>(null, "storingToNotNullArray", "dataflow.message.storing.array.null",
                                 "dataflow.message.storing.array.nullable");
  public static final NullabilityProblemKind<PsiExpression> nullableReturn = new NullabilityProblemKind<>(null, "nullableReturn");
  public static final NullabilityProblemKind<PsiExpression> nullableFunctionReturn =
    new NullabilityProblemKind<>(RE, "nullableFunctionReturn", "dataflow.message.return.nullable.from.notnull.function",
                                 "dataflow.message.return.nullable.from.notnull.function");
  public static final NullabilityProblemKind<PsiExpression> passingToNotNullParameter =
    new NullabilityProblemKind<>(RE, "passingToNotNullParameter", "dataflow.message.passing.null.argument",
                                 "dataflow.message.passing.nullable.argument");
  public static final NullabilityProblemKind<PsiMethodReferenceExpression> passingToNotNullMethodRefParameter =
    new NullabilityProblemKind<>(RE, "passingToNotNullMethodRefParameter", "dataflow.message.passing.nullable.argument.methodref");
  public static final NullabilityProblemKind<PsiExpression> passingToNonAnnotatedParameter =
    new NullabilityProblemKind<>(null, "passingToNonAnnotatedParameter", "dataflow.message.passing.null.argument.nonannotated",
                                 "dataflow.message.passing.nullable.argument.nonannotated");
  public static final NullabilityProblemKind<PsiMethodReferenceExpression> passingToNonAnnotatedMethodRefParameter =
    new NullabilityProblemKind<>(null, "passingToNonAnnotatedMethodRefParameter",
                                 "dataflow.message.passing.nullable.argument.methodref.nonannotated");
  // assumeNotNull problem is not reported, just used to force the argument to be not null
  public static final NullabilityProblemKind<PsiExpression> assumeNotNull = new NullabilityProblemKind<>(RE, "assumeNotNull");
  /**
   * noProblem is not reported and used to override another problem
   * @see ControlFlowAnalyzer#addCustomNullabilityProblem(PsiExpression, NullabilityProblemKind)
   * @see CFGBuilder#pushExpression(PsiExpression, NullabilityProblemKind)
   */
  public static final NullabilityProblemKind<PsiExpression> noProblem = new NullabilityProblemKind<>(null, "noProblem");

  /**
   * Creates a new {@link NullabilityProblem} of this kind using given anchor
   * @param anchor anchor to bind the problem to
   * @param expression shortest expression which is actually violates the nullability
   * @return newly created problem or null if anchor is null
   */
  @Contract("null, _ -> null")
  @Nullable
  public final NullabilityProblem<T> problem(@Nullable T anchor, @Nullable PsiExpression expression) {
    return anchor == null || this == noProblem ? null : new NullabilityProblem<>(this, anchor, expression);
  }

  /**
   * Returns the supplied problem with adjusted type parameter or null if supplied problem kind is not this kind
   *
   * @param problem problem to check
   * @return the supplied problem or null
   */
  @SuppressWarnings("unchecked")
  @Nullable
  public final NullabilityProblem<T> asMyProblem(NullabilityProblem<?> problem) {
    return problem != null && problem.myKind == this ? (NullabilityProblem<T>)problem : null;
  }

  /**
   * Executes given consumer if the supplied problem has the same kind as this kind
   *
   * @param problem a problem to check
   * @param consumer a consumer to execute. A problem anchor is supplied as the consumer argument.
   */
  public void ifMyProblem(NullabilityProblem<?> problem, Consumer<? super T> consumer) {
    NullabilityProblem<T> myProblem = asMyProblem(problem);
    if (myProblem != null) {
      consumer.accept(myProblem.getAnchor());
    }
  }

  @Override
  public String toString() {
    return myName;
  }

  @Nullable
  public static NullabilityProblem<?> fromContext(@NotNull PsiExpression expression,
                                                  Map<PsiExpression, NullabilityProblemKind<? super PsiExpression>> customNullabilityProblems) {
    if (TypeConversionUtil.isPrimitiveAndNotNull(expression.getType()) ||
        expression instanceof PsiReferenceExpression && ((PsiReferenceExpression)expression).resolve() instanceof PsiClass) {
      return null;
    }
    PsiExpression context = findTopExpression(expression);
    NullabilityProblemKind<? super PsiExpression> kind = customNullabilityProblems.get(context);
    if (kind != null) {
      return kind.problem(context, expression);
    }
    PsiElement parent = context.getParent();
    if (parent instanceof PsiReferenceExpression) {
      PsiElement resolved = ((PsiReferenceExpression)parent).resolve();
      if (resolved instanceof PsiMember && ((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC)) {
        return null;
      }
      PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiMethodCallExpression) {
        return callNPE.problem((PsiMethodCallExpression)grandParent, expression);
      }
      return fieldAccessNPE.problem(context, expression);
    }
    PsiType targetType = null;
    if (parent instanceof PsiLambdaExpression) {
      targetType = LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)parent);
    }
    else if (parent instanceof PsiReturnStatement) {
      targetType = PsiTypesUtil.getMethodReturnType(parent);
    }
    if (targetType != null && !PsiType.VOID.equals(targetType)) {
      if (TypeConversionUtil.isPrimitiveAndNotNull(targetType)) {
        return createUnboxingProblem(context, expression);
      }
      return nullableReturn.problem(context, expression);
    }
    if (parent instanceof PsiVariable) {
      PsiVariable var = (PsiVariable)parent;
      if (var.getType() instanceof PsiPrimitiveType) {
        return createUnboxingProblem(context, expression);
      }
      Nullability nullability = DfaPsiUtil.getElementNullability(var.getType(), var);
      if (nullability == Nullability.NOT_NULL) {
        return assigningToNotNull.problem(context, expression);
      }
    }
    if (parent instanceof PsiAssignmentExpression) {
      return getAssignmentProblem((PsiAssignmentExpression)parent, expression, context);
    }
    if (parent instanceof PsiExpressionList) {
      return getExpressionListProblem((PsiExpressionList)parent, expression, context);
    }
    if (parent instanceof PsiArrayInitializerExpression) {
      return getArrayInitializerProblem((PsiArrayInitializerExpression)parent, expression, context);
    }
    if (parent instanceof PsiTypeCastExpression) {
      if (TypeConversionUtil.isAssignableFromPrimitiveWrapper(context.getType())) {
        // Only casts to primitives are here; casts to objects were already traversed by findTopExpression
        return unboxingNullable.problem(context, expression);
      }
    }
    else if (parent instanceof PsiIfStatement || parent instanceof PsiWhileStatement || parent instanceof PsiDoWhileStatement ||
             parent instanceof PsiUnaryExpression || parent instanceof PsiConditionalExpression ||
             (parent instanceof PsiForStatement && ((PsiForStatement)parent).getCondition() == context) ||
             (parent instanceof PsiAssertStatement && ((PsiAssertStatement)parent).getAssertCondition() == context)) {
      return createUnboxingProblem(context, expression);
    }
    if (parent instanceof PsiSwitchBlock) {
      NullabilityProblem<PsiElement> problem = createUnboxingProblem(context, expression);
      return problem == null ? fieldAccessNPE.problem(context, expression) : problem;
    }
    if (parent instanceof PsiForeachStatement || parent instanceof PsiThrowStatement ||
        parent instanceof PsiSynchronizedStatement) {
      return fieldAccessNPE.problem(context, expression);
    }
    if (parent instanceof PsiNewExpression) {
      return innerClassNPE.problem((PsiNewExpression)parent, expression);
    }
    if (parent instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadic = (PsiPolyadicExpression)parent;
      IElementType type = polyadic.getOperationTokenType();
      boolean noUnboxing = (type == JavaTokenType.PLUS && TypeUtils.isJavaLangString(polyadic.getType())) ||
                           ((type == JavaTokenType.EQEQ || type == JavaTokenType.NE) &&
                            StreamEx.of(polyadic.getOperands()).noneMatch(op -> TypeConversionUtil.isPrimitiveAndNotNull(op.getType())));
      if (!noUnboxing) {
        return createUnboxingProblem(context, expression);
      }
    }
    if (parent instanceof PsiArrayAccessExpression) {
      PsiArrayAccessExpression arrayAccessExpression = (PsiArrayAccessExpression)parent;
      if (arrayAccessExpression.getArrayExpression() == context) {
        return arrayAccessNPE.problem(arrayAccessExpression, expression);
      }
      return createUnboxingProblem(context, expression);
    }
    return null;
  }

  @Nullable
  private static NullabilityProblem<?> getExpressionListProblem(@NotNull PsiExpressionList expressionList,
                                                                @NotNull PsiExpression expression,
                                                                @NotNull PsiExpression context) {
    if (expressionList.getParent() instanceof PsiSwitchLabelStatementBase) {
      return fieldAccessNPE.problem(context, expression);
    }
    PsiParameter parameter = MethodCallUtils.getParameterForArgument(context);
    PsiElement grandParent = expressionList.getParent();
    if (parameter != null) {
      if (parameter.getType() instanceof PsiPrimitiveType) {
        return createUnboxingProblem(context, expression);
      }
      if (grandParent instanceof PsiAnonymousClass) {
        grandParent = grandParent.getParent();
      }
      if (grandParent instanceof PsiCall) {
        PsiSubstitutor substitutor = ((PsiCall)grandParent).resolveMethodGenerics().getSubstitutor();
        Nullability nullability = DfaPsiUtil.getElementNullability(substitutor.substitute(parameter.getType()), parameter);
        if (nullability == Nullability.NOT_NULL) {
          return passingToNotNullParameter.problem(context, expression);
        }
        if (nullability == Nullability.UNKNOWN) {
          return passingToNonAnnotatedParameter.problem(context, expression);
        }
      }
    }
    else if (grandParent instanceof PsiCall && MethodCallUtils.isVarArgCall((PsiCall)grandParent)) {
      Nullability nullability = DfaPsiUtil.getVarArgComponentNullability(((PsiCall)grandParent).resolveMethod());
      if (nullability == Nullability.NOT_NULL) {
        return passingToNotNullParameter.problem(context, expression);
      }
    }
    return null;
  }

  @Nullable
  private static NullabilityProblem<?> getArrayInitializerProblem(@NotNull PsiArrayInitializerExpression initializer,
                                                                  @NotNull PsiExpression expression,
                                                                  @NotNull PsiExpression context) {
    PsiType type = initializer.getType();
    if (type instanceof PsiArrayType) {
      PsiType componentType = ((PsiArrayType)type).getComponentType();
      if (TypeConversionUtil.isPrimitiveAndNotNull(componentType)) {
        return createUnboxingProblem(context, expression);
      }
      Nullability nullability = DfaPsiUtil.getTypeNullability(componentType);
      if (nullability == Nullability.UNKNOWN) {
        if (initializer.getParent() instanceof PsiNewExpression) {
          PsiType expectedType = ExpectedTypeUtils.findExpectedType((PsiExpression)initializer.getParent(), false);
          if (expectedType instanceof PsiArrayType) {
            nullability = DfaPsiUtil.getTypeNullability(((PsiArrayType)expectedType).getComponentType());
          }
        }
      }
      if (nullability == Nullability.NOT_NULL) {
        return storingToNotNullArray.problem(context, expression);
      }
    }
    return null;
  }

  @Nullable
  private static NullabilityProblem<?> getAssignmentProblem(@NotNull PsiAssignmentExpression assignment,
                                                            @NotNull PsiExpression expression,
                                                            @NotNull PsiExpression context) {
    IElementType tokenType = assignment.getOperationTokenType();
    if (assignment.getRExpression() == context) {
      PsiExpression lho = PsiUtil.skipParenthesizedExprDown(assignment.getLExpression());
      if (lho != null) {
        PsiType type = lho.getType();
        if (tokenType.equals(JavaTokenType.PLUSEQ) && TypeUtils.isJavaLangString(type)) {
          return null;
        }
        if (type instanceof PsiPrimitiveType) {
          return createUnboxingProblem(context, expression);
        }
        Nullability nullability = Nullability.UNKNOWN;
        PsiVariable target = null;
        if (lho instanceof PsiReferenceExpression) {
          target = tryCast(((PsiReferenceExpression)lho).resolve(), PsiVariable.class);
          if (target != null) {
            nullability = DfaPsiUtil.getElementNullability(type, target);
          }
        }
        else {
          nullability = DfaPsiUtil.getTypeNullability(type);
        }
        boolean forceDeclaredNullity = !(target instanceof PsiParameter && target.getParent() instanceof PsiParameterList);
        if (forceDeclaredNullity && nullability == Nullability.NOT_NULL) {
          return (lho instanceof PsiArrayAccessExpression ? storingToNotNullArray : assigningToNotNull).problem(context, expression);
        }
        if (nullability == Nullability.UNKNOWN && lho instanceof PsiReferenceExpression) {
          PsiField field = tryCast(((PsiReferenceExpression)lho).resolve(), PsiField.class);
          if (field != null && !field.hasModifierProperty(PsiModifier.FINAL)) {
            return assigningToNonAnnotatedField.problem(context, expression);
          }
        }
      }
    }
    return null;
  }

  /**
   * Looks for top expression with the same nullability as given expression. That is: skips casts or conditionals, which don't unbox;
   * goes up from switch expression breaks or expression-branches.
   *
   * @param expression expression to find the top expression for
   * @return the top expression
   */
  @NotNull
  static PsiExpression findTopExpression(@NotNull PsiExpression expression) {
    PsiExpression context = expression;
    while (true) {
      PsiElement parent = context.getParent();
      if (parent instanceof PsiParenthesizedExpression || parent instanceof PsiTypeCastExpression ||
          (parent instanceof PsiConditionalExpression && ((PsiConditionalExpression)parent).getCondition() != context)) {
        if (TypeConversionUtil.isPrimitiveAndNotNull(((PsiExpression)parent).getType())) {
          return context;
        }
        context = (PsiExpression)parent;
        continue;
      }
      if (parent instanceof PsiExpressionStatement) {
        PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiSwitchLabeledRuleStatement) {
          PsiSwitchBlock block = ((PsiSwitchLabeledRuleStatement)grandParent).getEnclosingSwitchBlock();
          if (block instanceof PsiSwitchExpression) {
            context = (PsiExpression)block;
            continue;
          }
        }
      }
      if (parent instanceof PsiYieldStatement) {
        PsiSwitchExpression enclosing = ((PsiYieldStatement)parent).findEnclosingExpression();
        if (enclosing != null) {
          context = enclosing;
          continue;
        }
      }
      return context;
    }
  }

  private static NullabilityProblem<PsiElement> createUnboxingProblem(@NotNull PsiExpression context,
                                                                      @NotNull PsiExpression expression) {
    if (!TypeConversionUtil.isPrimitiveWrapper(context.getType())) return null;
    return unboxingNullable.problem(context, expression);
  }

  static List<NullabilityProblem<?>> postprocessNullabilityProblems(Collection<NullabilityProblem<?>> problems) {
    List<NullabilityProblem<?>> unchanged = new ArrayList<>();
    Map<PsiExpression, NullabilityProblem<?>> expressionToProblem = new HashMap<>();
    for (NullabilityProblem<?> problem : problems) {
      PsiExpression expression = problem.getDereferencedExpression();
      NullabilityProblemKind<?> kind = problem.getKind();
      if (expression == null) {
        unchanged.add(problem);
        continue;
      }
      if (innerClassNPE == kind || callNPE == kind || arrayAccessNPE == kind || fieldAccessNPE == kind) {
        // Qualifier-problems are reported on top-expression level for now as it's rare case to have
        // something complex in qualifier and we highlight not the qualifier itself, but something else (e.g. called method name)
        unchanged.add(problem.withExpression(findTopExpression(expression)));
        continue;
      }
      // Merge ternary problems reported for both branches into single problem
      while (true) {
        PsiExpression top = skipParenthesesAndObjectCastsUp(expression);
        PsiConditionalExpression ternary = tryCast(top.getParent(), PsiConditionalExpression.class);
        if (ternary != null) {
          PsiExpression otherBranch = null;
          if (ternary.getThenExpression() == top) {
            otherBranch = ternary.getElseExpression();
          }
          else if (ternary.getElseExpression() == top) {
            otherBranch = ternary.getThenExpression();
          }
          if (otherBranch != null) {
            otherBranch = skipParenthesesAndObjectCastsDown(otherBranch);
            NullabilityProblem<?> otherBranchProblem = expressionToProblem.remove(otherBranch);
            if (otherBranchProblem != null) {
              expression = ternary;
              problem = problem.withExpression(ternary);
              continue;
            }
          }
        }
        break;
      }
      expressionToProblem.put(expression, problem);
    }
    return StreamEx.of(unchanged, expressionToProblem.values()).toFlatList(Function.identity());
  }

  private static PsiExpression skipParenthesesAndObjectCastsDown(PsiExpression expression) {
    while (true) {
      if (expression instanceof PsiParenthesizedExpression) {
        expression = ((PsiParenthesizedExpression)expression).getExpression();
      }
      else if (expression instanceof PsiTypeCastExpression && !(expression.getType() instanceof PsiPrimitiveType)) {
        expression = ((PsiTypeCastExpression)expression).getOperand();
      }
      else {
        return expression;
      }
    }
  }

  @NotNull
  private static PsiExpression skipParenthesesAndObjectCastsUp(PsiExpression expression) {
    PsiExpression top = expression;
    while (true) {
      PsiElement parent = top.getParent();
      if (parent instanceof PsiParenthesizedExpression ||
          (parent instanceof PsiTypeCastExpression && !(((PsiTypeCastExpression)parent).getType() instanceof PsiPrimitiveType))) {
        top = (PsiExpression)parent;
      }
      else {
        return top;
      }
    }
  }

  /**
   * Represents a concrete nullability problem on PSI which consists of PSI element (anchor) and {@link NullabilityProblemKind}.
   * @param <T> a type of anchor element
   */
  public static final class NullabilityProblem<T extends PsiElement> {
    private final @NotNull NullabilityProblemKind<T> myKind;
    private final @NotNull T myAnchor;
    private final @Nullable PsiExpression myDereferencedExpression;

    NullabilityProblem(@NotNull NullabilityProblemKind<T> kind, @NotNull T anchor, @Nullable PsiExpression dereferencedExpression) {
      myKind = kind;
      myAnchor = anchor;
      myDereferencedExpression = dereferencedExpression;
    }

    @NotNull
    public T getAnchor() {
      return myAnchor;
    }

    /**
     * @return name of exception (or its superclass) which is thrown if violation occurs,
     * or null if no exception is thrown (e.g. when assigning null to variable annotated as notnull).
     */
    @Nullable
    public String thrownException() {
      return myKind.myException;
    }

    /**
     * @return a minimal nullable expression which causes the problem
     */
    @Nullable
    public PsiExpression getDereferencedExpression() {
      return myDereferencedExpression;
    }

    @NotNull
    public String getMessage(Map<PsiExpression, DataFlowInspectionBase.ConstantResult> expressions) {
      if (myKind.myAlwaysNullMessage == null || myKind.myNormalMessage == null) {
        throw new IllegalStateException("This problem kind has no message associated: " + myKind);
      }
      PsiExpression expression = PsiUtil.skipParenthesizedExprDown(getDereferencedExpression());
      if (expression != null) {
        if (ExpressionUtils.isNullLiteral(expression) || expressions.get(expression) == DataFlowInspectionBase.ConstantResult.NULL) {
          return myKind.myAlwaysNullMessage;
        }
      }
      return myKind.myNormalMessage;
    }

    @NotNull
    public NullabilityProblemKind<T> getKind() {
      return myKind;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof NullabilityProblem)) return false;
      NullabilityProblem<?> problem = (NullabilityProblem<?>)o;
      return myKind.equals(problem.myKind) && myAnchor.equals(problem.myAnchor) &&
             Objects.equals(myDereferencedExpression, problem.myDereferencedExpression);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myKind, myAnchor, myDereferencedExpression);
    }

    @Override
    public String toString() {
      return "[" + myKind + "] " + myAnchor.getText();
    }

    public NullabilityProblem<T> withExpression(PsiExpression expression) {
      return expression == myDereferencedExpression ? this : new NullabilityProblem<>(myKind, myAnchor, expression);
    }
  }
}

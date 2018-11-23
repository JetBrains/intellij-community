package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static com.intellij.codeInspection.InspectionsBundle.BUNDLE;

/**
 * Represents a kind of nullability problem
 * @param <T> a type of anchor element which could be associated with given nullability problem kind
 */
public class NullabilityProblemKind<T extends PsiElement> {
  private final String myName;
  private final String myAlwaysNullMessage;
  private final String myNormalMessage;
  private final boolean myThrows;

  private NullabilityProblemKind(boolean throwsException, @NotNull String name) {
    myThrows = throwsException;
    myName = name;
    myAlwaysNullMessage = null;
    myNormalMessage = null;
  }

  private NullabilityProblemKind(boolean throwsException, @NotNull String name, 
                                 @NotNull @PropertyKey(resourceBundle = BUNDLE) String message) {
    this(throwsException, name, message, message);
  }

  private NullabilityProblemKind(boolean throwsException, @NotNull String name,
                                 @NotNull @PropertyKey(resourceBundle = BUNDLE) String alwaysNullMessage,
                                 @NotNull @PropertyKey(resourceBundle = BUNDLE) String normalMessage) {
    myThrows = throwsException;
    myName = name;
    myAlwaysNullMessage = InspectionsBundle.message(alwaysNullMessage);
    myNormalMessage = InspectionsBundle.message(normalMessage);
  }

  public static final NullabilityProblemKind<PsiMethodCallExpression> callNPE =
    new NullabilityProblemKind<>(true, "callNPE", "dataflow.message.npe.method.invocation.sure", "dataflow.message.npe.method.invocation");
  public static final NullabilityProblemKind<PsiMethodReferenceExpression> callMethodRefNPE =
    new NullabilityProblemKind<>(true, "callMethodRefNPE", "dataflow.message.npe.methodref.invocation");
  public static final NullabilityProblemKind<PsiNewExpression> innerClassNPE =
    new NullabilityProblemKind<>(true, "innerClassNPE", "dataflow.message.npe.inner.class.construction.sure",
                                 "dataflow.message.npe.inner.class.construction");
  public static final NullabilityProblemKind<PsiExpression> fieldAccessNPE =
    new NullabilityProblemKind<>(true, "fieldAccessNPE", "dataflow.message.npe.field.access.sure", "dataflow.message.npe.field.access");
  public static final NullabilityProblemKind<PsiArrayAccessExpression> arrayAccessNPE =
    new NullabilityProblemKind<>(true, "arrayAccessNPE", "dataflow.message.npe.array.access.sure", "dataflow.message.npe.array.access");
  public static final NullabilityProblemKind<PsiElement> unboxingNullable =
    new NullabilityProblemKind<>(true, "unboxingNullable", "dataflow.message.unboxing");
  public static final NullabilityProblemKind<PsiExpression> assigningToNotNull =
    new NullabilityProblemKind<>(false, "assigningToNotNull", "dataflow.message.assigning.null", "dataflow.message.assigning.nullable");
  public static final NullabilityProblemKind<PsiExpression> storingToNotNullArray =
    new NullabilityProblemKind<>(false, "storingToNotNullArray", "dataflow.message.storing.array.null", 
                                 "dataflow.message.storing.array.nullable");
  public static final NullabilityProblemKind<PsiExpression> nullableReturn = new NullabilityProblemKind<>(false, "nullableReturn");
  public static final NullabilityProblemKind<PsiExpression> nullableFunctionReturn =
    new NullabilityProblemKind<>(true, "nullableFunctionReturn", "dataflow.message.return.nullable.from.notnull.function",
                                 "dataflow.message.return.nullable.from.notnull.function");
  public static final NullabilityProblemKind<PsiElement> passingNullableToNotNullParameter =
    new NullabilityProblemKind<>(true, "passingNullableToNotNullParameter");
  public static final NullabilityProblemKind<PsiElement> passingNullableArgumentToNonAnnotatedParameter =
    new NullabilityProblemKind<>(false, "passingNullableArgumentToNonAnnotatedParameter");
  public static final NullabilityProblemKind<PsiElement> assigningNullableValueToNonAnnotatedField =
    new NullabilityProblemKind<>(false, "assigningNullableValueToNonAnnotatedField");
  // assumeNotNull problem is not reported, just used to force the argument to be not null
  public static final NullabilityProblemKind<PsiExpression> assumeNotNull = new NullabilityProblemKind<>(true, "assumeNotNull");
  /**
   * noProblem is not reported and used to override another problem
   * @see ControlFlowAnalyzer#addCustomNullabilityProblem(PsiExpression, NullabilityProblemKind) 
   * @see CFGBuilder#pushExpression(PsiExpression, NullabilityProblemKind) 
   */
  public static final NullabilityProblemKind<PsiExpression> noProblem = new NullabilityProblemKind<>(false, "noProblem");

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
   * Returns true if the kind of supplied problem is the same as this kind
   *
   * @param problem problem to check
   * @return true if the kind of supplied problem is the same as this kind
   */
  public final boolean isMyProblem(@Nullable NullabilityProblem<?> problem) {
    return problem != null && problem.myKind == this;
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
  static NullabilityProblem<?> fromContext(@NotNull PsiExpression expression,
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
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
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
            target = ObjectUtils.tryCast(((PsiReferenceExpression)lho).resolve(), PsiVariable.class);
            if (target != null) {
              nullability = DfaPsiUtil.getElementNullability(type, target);
            }
          } else {
            nullability = DfaPsiUtil.getTypeNullability(type);
          }
          boolean forceDeclaredNullity = !(target instanceof PsiParameter && target.getParent() instanceof PsiParameterList);
          if (forceDeclaredNullity && nullability == Nullability.NOT_NULL) {
            return (lho instanceof PsiArrayAccessExpression ? storingToNotNullArray : assigningToNotNull).problem(context, expression);
          }
          if (nullability == Nullability.UNKNOWN && lho instanceof PsiReferenceExpression) {
            PsiField field = ObjectUtils.tryCast(((PsiReferenceExpression)lho).resolve(), PsiField.class);
            if (field != null && !field.hasModifierProperty(PsiModifier.FINAL)) {
              return assigningNullableValueToNonAnnotatedField.problem(context, expression);
            }
          }
        }
      }
    }
    if (parent instanceof PsiExpressionList) {
      if (parent.getParent() instanceof PsiSwitchLabelStatementBase) {
        return fieldAccessNPE.problem(context, expression);
      }
      PsiParameter parameter = MethodCallUtils.getParameterForArgument(context);
      if (parameter != null) {
        if (parameter.getType() instanceof PsiPrimitiveType) {
          return createUnboxingProblem(context, expression);
        }
        PsiElement grandParent = parent.getParent();
        if (grandParent instanceof PsiAnonymousClass) {
          grandParent = grandParent.getParent();
        }
        if (grandParent instanceof PsiCall) {
          PsiSubstitutor substitutor = ((PsiCall)grandParent).resolveMethodGenerics().getSubstitutor();
          Nullability nullability = DfaPsiUtil.getElementNullability(substitutor.substitute(parameter.getType()), parameter);
          if (nullability == Nullability.NOT_NULL) {
            return passingNullableToNotNullParameter.problem(context, expression);
          }
          if (nullability == Nullability.UNKNOWN) {
            return passingNullableArgumentToNonAnnotatedParameter.problem(context, expression);
          }
        }
      }
    }
    if (parent instanceof PsiIfStatement ||
        parent instanceof PsiWhileStatement ||
        parent instanceof PsiDoWhileStatement ||
        parent instanceof PsiUnaryExpression ||
        parent instanceof PsiConditionalExpression ||
        parent instanceof PsiTypeCastExpression ||
        parent instanceof PsiForStatement && ((PsiForStatement)parent).getCondition() == context ||
        parent instanceof PsiAssertStatement && ((PsiAssertStatement)parent).getAssertCondition() == context) {
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
    if (parent instanceof PsiArrayInitializerExpression) {
      PsiType type = ((PsiArrayInitializerExpression)parent).getType();
      if (type instanceof PsiArrayType) {
        PsiType componentType = ((PsiArrayType)type).getComponentType();
        if (TypeConversionUtil.isPrimitiveAndNotNull(componentType)) {
          return createUnboxingProblem(context, expression);
        }
        Nullability nullability = DfaPsiUtil.getTypeNullability(componentType);
        if (nullability == Nullability.UNKNOWN) {
          if (parent.getParent() instanceof PsiNewExpression) {
            PsiType expectedType = ExpectedTypeUtils.findExpectedType((PsiExpression)parent.getParent(), false);
            if (expectedType instanceof PsiArrayType) {
              nullability = DfaPsiUtil.getTypeNullability(((PsiArrayType)expectedType).getComponentType());
            }
          }
        }
        if (nullability == Nullability.NOT_NULL) {
          return storingToNotNullArray.problem(context, expression);
        }
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

  /**
   * Looks for top expression with the same nullability as given expression. That is: skips casts or conditionals, which don't unbox;
   * goes up from switch expression breaks or expression-branches.
   * 
   * @param expression expression to find the top expression for
   * @return the top expression
   */
  @NotNull
  private static PsiExpression findTopExpression(@NotNull PsiExpression expression) {
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
      if (parent instanceof PsiBreakStatement) {
        PsiElement exitedElement = ((PsiBreakStatement)parent).findExitedElement();
        if (exitedElement instanceof PsiSwitchExpression) {
          context = (PsiExpression)exitedElement;
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

  /**
   * Represents a concrete nullability problem on PSI which consists of PSI element (anchor) and {@link NullabilityProblemKind}.
   * @param <T> a type of anchor element
   */
  public static final class NullabilityProblem<T extends PsiElement> {
    private final @NotNull NullabilityProblemKind<? super T> myKind;
    private final @NotNull T myAnchor;
    private final @Nullable PsiExpression myDereferencedExpression;

    NullabilityProblem(@NotNull NullabilityProblemKind<? super T> kind, @NotNull T anchor, @Nullable PsiExpression dereferencedExpression) {
      myKind = kind;
      myAnchor = anchor;
      myDereferencedExpression = dereferencedExpression;
    }

    @NotNull
    public T getAnchor() {
      return myAnchor;
    }

    /**
     * @return true if this nullability violation results in the exception being thrown (e.g. dereference). 
     * False means that the execution may continue normally (e.g. assigning null to variable annotated as notnull).
     */
    public boolean throwsException() {
      return myKind.myThrows;
    }

    /**
     * @return a minimal nullable expression which causes the problem  
     */
    @Nullable
    public PsiExpression getDereferencedExpression() {
      return myDereferencedExpression;
    }
    
    @NotNull
    public String getMessage(Map<PsiExpression, DataFlowInstructionVisitor.ConstantResult> expressions) {
      if (myKind.myAlwaysNullMessage == null || myKind.myNormalMessage == null) {
        throw new IllegalStateException("This problem kind has no message associated: " + myKind);
      }
      PsiExpression expression = PsiUtil.skipParenthesizedExprDown(getDereferencedExpression());
      if (expression != null) {
        if (ExpressionUtils.isNullLiteral(expression) || expressions.get(expression) == DataFlowInstructionVisitor.ConstantResult.NULL) {
          return myKind.myAlwaysNullMessage;
        }
      }
      return myKind.myNormalMessage;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof NullabilityProblem)) return false;
      NullabilityProblem<?> problem = (NullabilityProblem<?>)o;
      return myKind.equals(problem.myKind) && myAnchor.equals(problem.myAnchor);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myKind, myAnchor);
    }

    @Override
    public String toString() {
      return "[" + myKind + "] " + myAnchor.getText();
    }
  }
}

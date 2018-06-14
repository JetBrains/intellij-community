// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.inference.InferenceFromSourceUtil;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaExpressionFactory;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.siyeh.ig.psiutils.ExpressionUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * @author Gregory.Shrago
 */
public class DfaUtil {
  @Nullable("null means DFA analysis has failed (too complex to analyze)")
  public static Collection<PsiExpression> getCachedVariableValues(@Nullable final PsiVariable variable, @Nullable final PsiElement context) {
    if (variable == null || context == null) return Collections.emptyList();

    final PsiElement codeBlock = DfaPsiUtil.getEnclosingCodeBlock(variable, context);
    if (codeBlock == null) return Collections.emptyList();

    final Map<PsiElement, ValuableInstructionVisitor.PlaceResult> value = getCachedPlaceResults(codeBlock);
    if (value == null) return null;

    ValuableInstructionVisitor.PlaceResult placeResult = value.get(context);
    final Collection<FList<PsiExpression>> concatenations = placeResult == null ? null : placeResult.myValues.get(variable);
    if (concatenations != null) {
      return ContainerUtil.map(concatenations, DfaUtil::concatenateExpressions);
    }
    return Collections.emptyList();
  }

  @Nullable("null means DFA analysis has failed (too complex to analyze)")
  private static Map<PsiElement, ValuableInstructionVisitor.PlaceResult> getCachedPlaceResults(@NotNull final PsiElement codeBlock) {
    return CachedValuesManager.getCachedValue(codeBlock, () -> {
      final ValuableInstructionVisitor visitor = new ValuableInstructionVisitor();
      RunnerResult runnerResult = new ValuableDataFlowRunner().analyzeMethod(codeBlock, visitor);
      return CachedValueProvider.Result.create(runnerResult == RunnerResult.OK ? visitor.myResults : null, codeBlock);
    });
  }

  /**
   * @deprecated for removal; use {@link #checkNullability(PsiVariable, PsiElement)}
   */
  @Deprecated
  @NotNull
  public static Nullness checkNullness(@Nullable final PsiVariable variable, @Nullable final PsiElement context) {
    return Nullness.fromNullability(checkNullability(variable, context));
  }

  @NotNull
  public static Nullability checkNullability(@Nullable final PsiVariable variable, @Nullable final PsiElement context) {
    Nullability nullability = tryCheckNullability(variable, context, null);
    return nullability != null ? nullability : Nullability.UNKNOWN;
  }

  @Nullable("null means DFA analysis has failed (too complex to analyze)")
  public static Nullability tryCheckNullability(@Nullable final PsiVariable variable,
                                             @Nullable final PsiElement context,
                                             @Nullable final PsiElement outerBlock) {
    if (variable == null || context == null) return null;

    final PsiElement codeBlock = outerBlock == null ? DfaPsiUtil.getEnclosingCodeBlock(variable, context) : outerBlock;
    Map<PsiElement, ValuableInstructionVisitor.PlaceResult> results = codeBlock == null ? null : getCachedPlaceResults(codeBlock);
    ValuableInstructionVisitor.PlaceResult placeResult = results == null ? null : results.get(context);
    if (placeResult == null) {
      return null;
    }
    if (placeResult.myNulls.contains(variable) && !placeResult.myNotNulls.contains(variable)) return Nullability.NULLABLE;
    if (placeResult.myNotNulls.contains(variable) && !placeResult.myNulls.contains(variable)) return Nullability.NOT_NULL;
    return Nullability.UNKNOWN;
  }

  @NotNull
  public static Collection<PsiExpression> getPossibleInitializationElements(@NotNull PsiElement qualifierExpression) {
    if (qualifierExpression instanceof PsiMethodCallExpression) {
      return Collections.singletonList((PsiMethodCallExpression)qualifierExpression);
    }
    if (qualifierExpression instanceof PsiReferenceExpression) {
      final PsiElement targetElement = ((PsiReferenceExpression)qualifierExpression).resolve();
      if (!(targetElement instanceof PsiVariable)) {
        return Collections.emptyList();
      }
      Collection<PsiExpression> variableValues = getCachedVariableValues((PsiVariable)targetElement, qualifierExpression);
      if (variableValues == null || variableValues.isEmpty()) {
        return DfaPsiUtil.getVariableAssignmentsInFile((PsiVariable)targetElement, false, qualifierExpression);
      }
      return variableValues;
    }
    if (qualifierExpression instanceof PsiLiteralExpression) {
      return Collections.singletonList((PsiLiteralExpression)qualifierExpression);
    }
    return Collections.emptyList();
  }

  @Nullable
  static PsiElement getClosureInside(Instruction instruction) {
    if (instruction instanceof MethodCallInstruction) {
      PsiCall anchor = ((MethodCallInstruction)instruction).getCallExpression();
      if (anchor instanceof PsiNewExpression) {
        return ((PsiNewExpression)anchor).getAnonymousClass();
      }
    }
    else if (instruction instanceof LambdaInstruction) {
      return ((LambdaInstruction)instruction).getLambdaExpression();
    }
    else if (instruction instanceof EmptyInstruction) {
      PsiElement anchor = ((EmptyInstruction)instruction).getAnchor();
      if (anchor instanceof PsiClass) {
        return anchor;
      }
    }
    return null;
  }

  /**
   * @deprecated for removal; use {@link #inferMethodNullability(PsiMethod)}
   */
  @Deprecated
  @NotNull
  public static Nullness inferMethodNullity(PsiMethod method) {
    return Nullness.fromNullability(inferMethodNullability(method));
  }

  @NotNull
  public static Nullability inferMethodNullability(PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    if (body == null || PsiUtil.resolveClassInType(method.getReturnType()) == null) {
      return Nullability.UNKNOWN;
    }

    return inferBlockNullability(body, InferenceFromSourceUtil.suppressNullable(method));
  }

  @NotNull
  public static Nullability inferLambdaNullability(PsiLambdaExpression lambda) {
    final PsiElement body = lambda.getBody();
    if (body == null || LambdaUtil.getFunctionalInterfaceReturnType(lambda) == null) {
      return Nullability.UNKNOWN;
    }

    return inferBlockNullability(body, false);
  }

  @NotNull
  private static Nullability inferBlockNullability(PsiElement body, boolean suppressNullable) {
    final AtomicBoolean hasNulls = new AtomicBoolean();
    final AtomicBoolean hasNotNulls = new AtomicBoolean();
    final AtomicBoolean hasUnknowns = new AtomicBoolean();

    final StandardDataFlowRunner dfaRunner = new StandardDataFlowRunner();
    final RunnerResult rc = dfaRunner.analyzeMethod(body, new StandardInstructionVisitor() {
      @Override
      public DfaInstructionState[] visitCheckReturnValue(CheckReturnValueInstruction instruction,
                                                         DataFlowRunner runner,
                                                         DfaMemoryState memState) {
        if(PsiTreeUtil.isAncestor(body, instruction.getReturn(), false)) {
          DfaValue returned = memState.peek();
          if (memState.isNull(returned)) {
            hasNulls.set(true);
          }
          else if (memState.isNotNull(returned)) {
            hasNotNulls.set(true);
          }
          else {
            hasUnknowns.set(true);
          }
        }
        return super.visitCheckReturnValue(instruction, runner, memState);
      }
    });

    if (rc == RunnerResult.OK) {
      if (hasNulls.get()) {
        return suppressNullable ? Nullability.UNKNOWN : Nullability.NULLABLE;
      }
      if (hasNotNulls.get() && !hasUnknowns.get()) {
        return Nullability.NOT_NULL;
      }
    }

    return Nullability.UNKNOWN;
  }

  static DfaValue getPossiblyNonInitializedValue(@NotNull DfaValueFactory factory, @NotNull PsiField target, @NotNull PsiElement context) {
    if (target.getType() instanceof PsiPrimitiveType) return null;
    PsiMethod placeMethod = PsiTreeUtil.getParentOfType(context, PsiMethod.class, false, PsiClass.class, PsiLambdaExpression.class);
    if (placeMethod == null) return null;

    PsiClass placeClass = placeMethod.getContainingClass();
    if (placeClass == null || placeClass != target.getContainingClass()) return null;
    if (!placeMethod.hasModifierProperty(PsiModifier.STATIC) && target.hasModifierProperty(PsiModifier.STATIC)) return null;
    if (getAccessOffset(placeMethod) >= getWriteOffset(target)) return null;

    return factory.createTypeValue(target.getType(), Nullability.NULLABLE);
  }

  private static int getWriteOffset(PsiField target) {
    // Final field: written either in field initializer or in class initializer block which directly writes this field
    // Non-final field: written either in field initializer, in class initializer which directly writes this field or calls any method,
    //    or in other field initializer which directly writes this field or calls any method
    boolean isFinal = target.hasModifierProperty(PsiModifier.FINAL);
    int offset = Integer.MAX_VALUE;
    if (target.getInitializer() != null) {
      offset = target.getInitializer().getTextRange().getStartOffset();
      if (isFinal) return offset;
    }
    PsiClass aClass = Objects.requireNonNull(target.getContainingClass());
    PsiClassInitializer[] initializers = aClass.getInitializers();
    Predicate<PsiElement> writesToTarget = element ->
      !PsiTreeUtil.processElements(element, e -> !(e instanceof PsiExpression) ||
                                                 !PsiUtil.isAccessedForWriting((PsiExpression)e) ||
                                                 !ExpressionUtils.isReferenceTo((PsiExpression)e, target));
    Predicate<PsiElement> hasSideEffectCall = element -> !PsiTreeUtil.findChildrenOfType(element, PsiMethodCallExpression.class).stream()
      .map(PsiMethodCallExpression::resolveMethod).allMatch(method -> method != null && JavaMethodContractUtil.isPure(method));
    for (PsiClassInitializer initializer : initializers) {
      if (initializer.hasModifierProperty(PsiModifier.STATIC) != target.hasModifierProperty(PsiModifier.STATIC)) continue;
      if (!isFinal && hasSideEffectCall.test(initializer)) {
        // non-final field could be written indirectly (via method call), so assume it's written in the first applicable initializer
        offset = Math.min(offset, initializer.getTextRange().getStartOffset());
        break;
      }
      if (writesToTarget.test(initializer)) {
        offset = Math.min(offset, initializer.getTextRange().getStartOffset());
        if (isFinal) return offset;
        break;
      }
    }
    if (!isFinal) {
      for (PsiField field : aClass.getFields()) {
        if (field.hasModifierProperty(PsiModifier.STATIC) != target.hasModifierProperty(PsiModifier.STATIC)) continue;
        if (hasSideEffectCall.test(field.getInitializer()) || writesToTarget.test(field)) {
          offset = Math.min(offset, field.getTextRange().getStartOffset());
          break;
        }
      }
    }
    return offset;
  }

  private static int getAccessOffset(PsiMethod referrer) {
    PsiClass aClass = Objects.requireNonNull(referrer.getContainingClass());
    boolean isStatic = referrer.hasModifierProperty(PsiModifier.STATIC);
    for (PsiField field : aClass.getFields()) {
      if (field.hasModifierProperty(PsiModifier.STATIC) != isStatic) continue;
      PsiExpression initializer = field.getInitializer();
      Predicate<PsiExpression> callToMethod = (PsiExpression e) -> {
        if (!(e instanceof PsiMethodCallExpression)) return false;
        PsiMethodCallExpression call = (PsiMethodCallExpression)e;
        return call.getMethodExpression().isReferenceTo(referrer) &&
               (isStatic || ExpressionUtil.isEffectivelyUnqualified(call.getMethodExpression()));
      };
      if (ExpressionUtils.isMatchingChildAlwaysExecuted(initializer, callToMethod)) {
        // current method is definitely called from some field initialization
        return field.getTextRange().getStartOffset();
      }
    }
    return Integer.MAX_VALUE; // accessed after initialization or at unknown moment
  }

  public static boolean hasInitializationHacks(@NotNull PsiField field) {
    PsiClass containingClass = field.getContainingClass();
    return containingClass != null && System.class.getName().equals(containingClass.getQualifiedName());
  }

  public static boolean ignoreInitializer(PsiVariable variable) {
    // Skip boolean constant fields as they usually used as control knobs to modify program logic
    // it's better to analyze both true and false values even if it's predefined
    PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
    return initializer != null &&
           variable instanceof PsiField &&
           variable.hasModifierProperty(PsiModifier.FINAL) &&
           variable.getType().equals(PsiType.BOOLEAN) &&
           (ExpressionUtils.isLiteral(initializer, Boolean.TRUE) || ExpressionUtils.isLiteral(initializer, Boolean.FALSE));
  }

  static boolean isEffectivelyUnqualified(DfaVariableValue variableValue) {
    return variableValue.getQualifier() == null ||
     variableValue.getQualifier().getSource() instanceof DfaExpressionFactory.ThisSource;
  }

  public static boolean hasImplicitImpureSuperCall(PsiClass aClass, PsiMethod constructor) {
    PsiClass superClass = aClass.getSuperClass();
    if (superClass == null) return false;
    PsiElement superCtor = JavaResolveUtil.resolveImaginarySuperCallInThisPlace(constructor, constructor.getProject(), superClass);
    if (!(superCtor instanceof PsiMethod)) return false;
    return !JavaMethodContractUtil.isPure((PsiMethod)superCtor);
  }

  /**
   * Returns a surrounding PSI element which should be analyzed via DFA
   * (e.g. passed to {@link DataFlowRunner#analyzeMethodRecursively(PsiElement, StandardInstructionVisitor)}) to cover given expression.
   *
   * @param expression expression to cover
   * @return a dataflow context; null if no applicable context found.
   */
  @Nullable
  static PsiElement getDataflowContext(PsiExpression expression) {
    PsiMember member = PsiTreeUtil.getParentOfType(expression, PsiMember.class);
    if (member instanceof PsiField || member instanceof PsiClassInitializer) return member.getContainingClass();
    if (member instanceof PsiMethod) {
      return ((PsiMethod)member).isConstructor() ? member.getContainingClass() : ((PsiMethod)member).getBody();
    }
    return null;
  }

  /**
   * Tries to evaluate boolean condition using dataflow analysis.
   * Currently is limited to comparisons like {@code a > b} and constant expressions.
   *
   * @param condition condition to evaluate
   * @return evaluated value or null if cannot be evaluated
   */
  @Nullable
  public static Boolean evaluateCondition(@Nullable PsiExpression condition) {
    condition = PsiUtil.skipParenthesizedExprDown(condition);
    if (condition == null || !PsiType.BOOLEAN.equals(condition.getType())) return null;
    Object o = ExpressionUtils.computeConstantExpression(condition);
    if (o instanceof Boolean) return (Boolean)o;
    if (!(condition instanceof PsiBinaryExpression)) return null;
    PsiBinaryExpression binOp = (PsiBinaryExpression)condition;
    PsiElement context = getDataflowContext(condition);
    if (context == null) return null;
    class MyVisitor extends StandardInstructionVisitor {
      boolean myTrueReachable = false;
      boolean myFalseReachable = false;

      @Override
      public DfaInstructionState[] visitBinop(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
        DfaInstructionState[] states = super.visitBinop(instruction, runner, memState);
        if (instruction.getPsiAnchor() == binOp) {
          myTrueReachable |= instruction.isTrueReachable();
          myFalseReachable |= instruction.isFalseReachable();
          if (myTrueReachable && myFalseReachable) {
            runner.cancel();
          }
        }
        return states;
      }
    }
    MyVisitor visitor = new MyVisitor();
    if (new DataFlowRunner().analyzeMethodRecursively(context, visitor) == RunnerResult.OK) {
      if (visitor.myTrueReachable != visitor.myFalseReachable) {
        return visitor.myTrueReachable;
      }
    }
    return null;
  }

  private static class ValuableInstructionVisitor extends StandardInstructionVisitor {
    final Map<PsiElement, PlaceResult> myResults = ContainerUtil.newHashMap();

    static class PlaceResult {
      final MultiValuesMap<PsiVariable, FList<PsiExpression>> myValues = new MultiValuesMap<>(true);
      final Set<PsiVariable> myNulls = new THashSet<>();
      final Set<PsiVariable> myNotNulls = new THashSet<>();
    }

    @Override
    public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
      PsiExpression place = instruction.getExpression();
      if (place != null) {
        PlaceResult result = myResults.computeIfAbsent(place, __ -> new PlaceResult());
        ((ValuableDataFlowRunner.MyDfaMemoryState)memState).forVariableStates((variableValue, value) -> {
          ValuableDataFlowRunner.ValuableDfaVariableState state = (ValuableDataFlowRunner.ValuableDfaVariableState)value;
          final FList<PsiExpression> concatenation = state.myConcatenation;
          if (!concatenation.isEmpty() && isEffectivelyUnqualified(variableValue)) {
            PsiModifierListOwner element = variableValue.getPsiVariable();
            if (element instanceof PsiVariable) {
              result.myValues.put((PsiVariable)element, concatenation);
            }
          }
        });
        DfaValue value = instruction.getValue();
        if (value instanceof DfaVariableValue && isEffectivelyUnqualified((DfaVariableValue)value)) {
          PsiModifierListOwner element = ((DfaVariableValue)value).getPsiVariable();
          if (element instanceof PsiVariable) {
            if (memState.isNotNull(value)) {
              result.myNotNulls.add((PsiVariable)element);
            }
            if (memState.isNull(value)) {
              result.myNulls.add((PsiVariable)element);
            }
          }
        }
      }
      return super.visitPush(instruction, runner, memState);
    }

    @Override
    public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState _memState) {
      final Instruction nextInstruction = runner.getInstruction(instruction.getIndex() + 1);

      ValuableDataFlowRunner.MyDfaMemoryState memState = (ValuableDataFlowRunner.MyDfaMemoryState)_memState;
      final DfaValue dfaSource = memState.pop();
      final DfaValue dfaDest = memState.pop();

      if (dfaDest instanceof DfaVariableValue) {
        DfaVariableValue var = (DfaVariableValue)dfaDest;
        final PsiExpression rightValue = instruction.getRExpression();
        final PsiElement parent = rightValue == null ? null : rightValue.getParent();
        final IElementType type = parent instanceof PsiAssignmentExpression
                                  ? ((PsiAssignmentExpression)parent).getOperationTokenType() : JavaTokenType.EQ;
        // store current value - to use in case of '+='
        final FList<PsiExpression> prevValue = ((ValuableDataFlowRunner.ValuableDfaVariableState)memState.getVariableState(var)).myConcatenation;
        memState.setVarValue(var, dfaSource);
        // state may have been changed so re-retrieve it
        final ValuableDataFlowRunner.ValuableDfaVariableState curState = (ValuableDataFlowRunner.ValuableDfaVariableState)memState.getVariableState(var);
        final FList<PsiExpression> curValue = curState.myConcatenation;
        final FList<PsiExpression> nextValue;
        if (type == JavaTokenType.PLUSEQ && !prevValue.isEmpty()) {
          nextValue = prevValue.prepend(rightValue);
        }
        else {
          nextValue = curValue.isEmpty() && rightValue != null ? curValue.prepend(rightValue) : curValue;
        }
        memState.setVariableState(var, curState.withExpression(nextValue));
      }
      memState.push(dfaDest);
      return new DfaInstructionState[]{new DfaInstructionState(nextInstruction, memState)};
    }
  }

  private static PsiExpression concatenateExpressions(FList<PsiExpression> concatenation) {
    if (concatenation.size() == 1) {
      return concatenation.getHead();
    }
    String text = StringUtil.join(ContainerUtil.reverse(new ArrayList<>(concatenation)), PsiElement::getText, "+");
    try {
      return JavaPsiFacade.getElementFactory(concatenation.getHead().getProject()).createExpressionFromText(text, concatenation.getHead());
    }
    catch (IncorrectOperationException e) {
      return concatenation.getHead();
    }
  }

  public static boolean isNaN(Object value) {
    if (value instanceof Double && ((Double)value).isNaN()) return true;
    if (value instanceof Float && ((Float)value).isNaN()) return true;
    return false;
  }
}

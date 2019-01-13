// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.inference.InferenceFromSourceUtil;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Gregory.Shrago
 */
public class DfaUtil {
  private static final Object UNKNOWN_VALUE = ObjectUtils.sentinel("UNKNOWN_VALUE");

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
    if (PsiUtil.resolveClassInType(method.getReturnType()) == null) {
      return Nullability.UNKNOWN;
    }

    return inferBlockNullability(method, InferenceFromSourceUtil.suppressNullable(method));
  }

  @NotNull
  public static Nullability inferLambdaNullability(PsiLambdaExpression lambda) {
    if (LambdaUtil.getFunctionalInterfaceReturnType(lambda) == null) {
      return Nullability.UNKNOWN;
    }

    return inferBlockNullability(lambda, false);
  }

  @NotNull
  private static Nullability inferBlockNullability(PsiParameterListOwner owner, boolean suppressNullable) {
    PsiElement body = owner.getBody();
    if (body == null) return Nullability.UNKNOWN;

    final StandardDataFlowRunner dfaRunner = new StandardDataFlowRunner();
    class BlockNullabilityVisitor extends StandardInstructionVisitor {
      boolean hasNulls = false;
      boolean hasNotNulls = false;
      boolean hasUnknowns = false;

      @Override
      protected void checkReturnValue(@NotNull DfaValue value,
                                      @NotNull PsiExpression expression,
                                      @NotNull PsiParameterListOwner context,
                                      @NotNull DfaMemoryState state) {
        if (context == owner) {
          if (TypeConversionUtil.isPrimitiveAndNotNull(expression.getType()) || state.isNotNull(value)) {
            hasNotNulls = true;
          }
          else if (state.isNull(value)) {
            hasNulls = true;
          }
          else {
            hasUnknowns = true;
          }
        }
      }
    }
    BlockNullabilityVisitor visitor = new BlockNullabilityVisitor();
    final RunnerResult rc = dfaRunner.analyzeMethod(body, visitor);

    if (rc == RunnerResult.OK) {
      if (visitor.hasNulls) {
        return suppressNullable ? Nullability.UNKNOWN : Nullability.NULLABLE;
      }
      if (visitor.hasNotNulls && !visitor.hasUnknowns) {
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
    if (variable instanceof PsiField && variable.hasModifierProperty(PsiModifier.FINAL) && variable.getType().equals(PsiType.BOOLEAN)) {
      // Skip boolean constant fields as they usually used as control knobs to modify program logic
      // it's better to analyze both true and false values even if it's predefined
      PsiLiteralExpression initializer = tryCast(PsiUtil.skipParenthesizedExprDown(variable.getInitializer()), PsiLiteralExpression.class);
      return initializer != null && initializer.getValue() instanceof Boolean;
    }
    return false;
  }

  static boolean isEffectivelyUnqualified(DfaVariableValue variableValue) {
    return variableValue.getQualifier() == null ||
     variableValue.getQualifier().getDescriptor() instanceof DfaExpressionFactory.ThisDescriptor;
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
    CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(condition);
    return result == null ? null : tryCast(result.getExpressionValue(condition), Boolean.class);
  }

  public static boolean isComparedByEquals(PsiType type) {
    return type != null && (TypeUtils.isJavaLangString(type) || TypeConversionUtil.isPrimitiveWrapper(type));
  }

  public static DfaValue boxUnbox(DfaValue value, @Nullable PsiType type) {
    if (TypeConversionUtil.isPrimitiveWrapper(type)) {
      if (value instanceof DfaConstValue ||
          (value instanceof DfaVariableValue && TypeConversionUtil.isPrimitiveAndNotNull(value.getType()))) {
        DfaValue boxed = value.getFactory().getBoxedFactory().createBoxed(value, type);
        return boxed == null ? DfaUnknownValue.getInstance() : boxed;
      }
    }
    if (TypeConversionUtil.isPrimitiveAndNotNull(type)) {
      if (value instanceof DfaBoxedValue ||
          (value instanceof DfaVariableValue && TypeConversionUtil.isPrimitiveWrapper(value.getType()))) {
        return SpecialField.UNBOX.createValue(value.getFactory(), value);
      }
    }
    return value;
  }

  /**
   * Returns the value of given expression calculated via dataflow; or null if value is null or unknown.
   * The expression context is not taken into account; only expression itself.
   *
   * @param expression expression to analyze
   * @return expression value if known
   */
  public static Object computeValue(PsiExpression expression) {
    PsiExpression expressionToAnalyze = PsiUtil.skipParenthesizedExprDown(expression);
    if (expressionToAnalyze == null) return null;
    Object computed = ExpressionUtils.computeConstantExpression(expression);
    if (computed != null) return computed;

    DataFlowRunner runner = new StandardDataFlowRunner(false, expression);
    class Visitor extends StandardInstructionVisitor {
      Object exprValue;

      @Override
      protected void beforeExpressionPush(@NotNull DfaValue value,
                                          @NotNull PsiExpression expr,
                                          @Nullable TextRange range,
                                          @NotNull DfaMemoryState state) {
        super.beforeExpressionPush(value, expr, range, state);
        if (expr != expressionToAnalyze) return;
        Object newValue;
        if (value instanceof DfaConstValue) {
          newValue = ((DfaConstValue)value).getValue();
        } else {
          newValue = UNKNOWN_VALUE;
        }
        if (exprValue == null) {
          exprValue = newValue;
        } else if (exprValue != newValue) {
          exprValue = UNKNOWN_VALUE;
        }
        if (exprValue == UNKNOWN_VALUE) {
          runner.cancel();
        }
      }
    }
    Visitor visitor = new Visitor();
    RunnerResult result = runner.analyzeMethod(expressionToAnalyze, visitor);
    if (result == RunnerResult.OK && visitor.exprValue != UNKNOWN_VALUE) {
      return visitor.exprValue;
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

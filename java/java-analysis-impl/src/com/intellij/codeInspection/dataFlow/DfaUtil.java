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

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
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

  @NotNull
  public static Nullness checkNullness(@Nullable final PsiVariable variable, @Nullable final PsiElement context) {
    return checkNullness(variable, context, null);
  }

  @NotNull
  public static Nullness checkNullness(@Nullable final PsiVariable variable,
                                       @Nullable final PsiElement context,
                                       @Nullable final PsiElement outerBlock) {
    if (variable == null || context == null) return Nullness.UNKNOWN;

    final PsiElement codeBlock = outerBlock == null ? DfaPsiUtil.getEnclosingCodeBlock(variable, context) : outerBlock;
    Map<PsiElement, ValuableInstructionVisitor.PlaceResult> results = codeBlock == null ? null : getCachedPlaceResults(codeBlock);
    ValuableInstructionVisitor.PlaceResult placeResult = results == null ? null : results.get(context);
    if (placeResult == null) {
      return Nullness.UNKNOWN;
    }
    if (placeResult.myNulls.contains(variable) && !placeResult.myNotNulls.contains(variable)) return Nullness.NULLABLE;
    if (placeResult.myNotNulls.contains(variable) && !placeResult.myNulls.contains(variable)) return Nullness.NOT_NULL;
    return Nullness.UNKNOWN;
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

  @NotNull
  public static Nullness inferMethodNullity(PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    if (body == null || PsiUtil.resolveClassInType(method.getReturnType()) == null) {
      return Nullness.UNKNOWN;
    }

    return inferBlockNullity(body, InferenceFromSourceUtil.suppressNullable(method));
  }

  @NotNull
  public static Nullness inferLambdaNullity(PsiLambdaExpression lambda) {
    final PsiElement body = lambda.getBody();
    if (body == null || LambdaUtil.getFunctionalInterfaceReturnType(lambda) == null) {
      return Nullness.UNKNOWN;
    }

    return inferBlockNullity(body, false);
  }

  @NotNull
  private static Nullness inferBlockNullity(PsiElement body, boolean suppressNullable) {
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
        return suppressNullable ? Nullness.UNKNOWN : Nullness.NULLABLE;
      }
      if (hasNotNulls.get() && !hasUnknowns.get()) {
        return Nullness.NOT_NULL;
      }
    }

    return Nullness.UNKNOWN;
  }

  static DfaValue getPossiblyNonInitializedValue(@NotNull DfaValueFactory factory, @NotNull PsiField target, @NotNull PsiElement context) {
    if (target.getType() instanceof PsiPrimitiveType) return null;
    PsiMethod placeMethod = PsiTreeUtil.getParentOfType(context, PsiMethod.class, false, PsiClass.class, PsiLambdaExpression.class);
    if (placeMethod == null) return null;

    PsiClass placeClass = placeMethod.getContainingClass();
    if (placeClass == null || placeClass != target.getContainingClass()) return null;
    if (!placeMethod.hasModifierProperty(PsiModifier.STATIC) && target.hasModifierProperty(PsiModifier.STATIC)) return null;
    if (getAccessOffset(placeMethod) >= getWriteOffset(target)) return null;

    return factory.createTypeValue(target.getType(), Nullness.NULLABLE);
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
      .map(PsiMethodCallExpression::resolveMethod).allMatch(method -> method != null && ControlFlowAnalyzer.isPure(method));
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

  static boolean isInsideConstructorOrInitializer(PsiElement element) {
    while (element != null) {
      if (element instanceof PsiClass) return true;
      element = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiClassInitializer.class);
      if (element instanceof PsiClassInitializer) return true;
      if (element instanceof PsiMethod) {
        if (((PsiMethod)element).isConstructor()) return true;

        final PsiClass containingClass = ((PsiMethod)element).getContainingClass();
        return !InheritanceUtil.processSupers(containingClass, true,
                                              psiClass -> !canCallMethodsInConstructors(psiClass, psiClass != containingClass));

      }
    }
    return false;
  }

  private static boolean canCallMethodsInConstructors(PsiClass aClass, boolean virtual) {
    for (PsiMethod constructor : aClass.getConstructors()) {
      if (!constructor.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return true;

      PsiCodeBlock body = constructor.getBody();
      if (body == null) continue;

      for (PsiMethodCallExpression call : SyntaxTraverser.psiTraverser().withRoot(body).filter(PsiMethodCallExpression.class)) {
        PsiReferenceExpression methodExpression = call.getMethodExpression();
        if (methodExpression.textMatches(PsiKeyword.THIS) || methodExpression.textMatches(PsiKeyword.SUPER)) continue;
        if (!virtual) return true;

        PsiMethod target = call.resolveMethod();
        if (target != null && PsiUtil.canBeOverridden(target)) return true;
      }
    }

    return false;
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
      PsiExpression place = instruction.getPlace();
      if (place != null) {
        PlaceResult result = myResults.computeIfAbsent(place, __ -> new PlaceResult());
        ((ValuableDataFlowRunner.MyDfaMemoryState)memState).forVariableStates((variableValue, value) -> {
          ValuableDataFlowRunner.ValuableDfaVariableState state = (ValuableDataFlowRunner.ValuableDfaVariableState)value;
          final FList<PsiExpression> concatenation = state.myConcatenation;
          if (!concatenation.isEmpty() && variableValue.getQualifier() == null) {
            PsiModifierListOwner element = variableValue.getPsiVariable();
            if (element instanceof PsiVariable) {
              result.myValues.put((PsiVariable)element, concatenation);
            }
          }
        });
        DfaValue value = instruction.getValue();
        if (value instanceof DfaVariableValue && ((DfaVariableValue)value).getQualifier() == null) {
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
    String text = StringUtil
      .join(ContainerUtil.reverse(new ArrayList<>(concatenation)), expression -> expression.getText(), "+");
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

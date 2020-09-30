// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_COLLECTIONS;
import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Gregory.Shrago
 */
public final class DfaUtil {

  public static @NotNull Collection<PsiExpression> getVariableValues(@Nullable PsiVariable variable, @Nullable PsiElement context) {
    if (variable == null || context == null) return Collections.emptyList();

    final PsiCodeBlock codeBlock = tryCast(getEnclosingCodeBlock(variable, context), PsiCodeBlock.class);
    if (codeBlock == null) return Collections.emptyList();
    PsiElement[] defs = DefUseUtil.getDefs(codeBlock, variable, context);

    List<PsiExpression> results = new ArrayList<>();
    for (PsiElement def : defs) {
      if (def instanceof PsiLocalVariable) {
        ContainerUtil.addIfNotNull(results, ((PsiLocalVariable)def).getInitializer());
      }
      else if (def instanceof PsiReferenceExpression) {
        PsiAssignmentExpression assignment = tryCast(def.getParent(), PsiAssignmentExpression.class);
        if(assignment != null && assignment.getLExpression() == def) {
          ContainerUtil.addIfNotNull(results, unrollConcatenation(assignment, variable, codeBlock));
        }
      }
      else if (def instanceof PsiExpression) {
        results.add((PsiExpression)def);
      }
    }
    return results;
  }

  private static PsiElement getEnclosingCodeBlock(final PsiVariable variable, final PsiElement context) {
    PsiElement codeBlock;
    if (variable instanceof PsiParameter) {
      codeBlock = ((PsiParameter)variable).getDeclarationScope();
      if (codeBlock instanceof PsiMethod) {
        codeBlock = ((PsiMethod)codeBlock).getBody();
      }
    }
    else if (variable instanceof PsiLocalVariable) {
      codeBlock = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
    }
    else {
      codeBlock = DfaPsiUtil.getTopmostBlockInSameClass(context);
    }
    while (codeBlock != null) {
      PsiAnonymousClass anon = PsiTreeUtil.getParentOfType(codeBlock, PsiAnonymousClass.class);
      if (anon == null) break;
      codeBlock = PsiTreeUtil.getParentOfType(anon, PsiCodeBlock.class);
    }
    return codeBlock;
  }

  private static PsiExpression unrollConcatenation(PsiAssignmentExpression assignment, PsiVariable variable, PsiCodeBlock block) {
    List<PsiExpression> operands = new ArrayList<>();
    while (true) {
      if (assignment == null) return null;
      PsiExpression rExpression = assignment.getRExpression();
      if (rExpression == null) return null;
      operands.add(rExpression);
      IElementType type = assignment.getOperationTokenType();
      if (type.equals(JavaTokenType.EQ)) break;
      if (!type.equals(JavaTokenType.PLUSEQ)) {
        return null;
      }
      PsiElement[] previous = DefUseUtil.getDefs(block, variable, assignment);
      if (previous.length != 1) return null;
      PsiElement def = previous[0];
      if (def instanceof PsiLocalVariable) {
        PsiExpression initializer = ((PsiLocalVariable)def).getInitializer();
        if (initializer == null) return null;
        operands.add(initializer);
        break;
      }
      else if (def instanceof PsiReferenceExpression) {
        assignment = tryCast(def.getParent(), PsiAssignmentExpression.class);
      }
      else return null;
    }
    if (operands.size() == 1) {
      return operands.get(0);
    }
    return JavaPsiFacade.getElementFactory(block.getProject()).createExpressionFromText(
      StreamEx.ofReversed(operands).map(op -> ParenthesesUtils.getText(op, PsiPrecedenceUtil.ADDITIVE_PRECEDENCE)).joining("+"),
      assignment);
  }

  /**
   * @deprecated use {@link NullabilityUtil#getExpressionNullability(PsiExpression, boolean)}
   * Note that variable parameter is not used at all now.
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  @Deprecated
  public static @NotNull Nullability checkNullability(final @Nullable PsiVariable variable, final @Nullable PsiElement context) {
    if (context instanceof PsiExpression) {
      return NullabilityUtil.getExpressionNullability((PsiExpression)context, true);
    }
    return Nullability.UNKNOWN;
  }

  public static @NotNull Collection<PsiExpression> getPossibleInitializationElements(@NotNull PsiElement qualifierExpression) {
    if (qualifierExpression instanceof PsiMethodCallExpression) {
      return Collections.singletonList((PsiMethodCallExpression)qualifierExpression);
    }
    if (qualifierExpression instanceof PsiReferenceExpression) {
      final PsiElement targetElement = ((PsiReferenceExpression)qualifierExpression).resolve();
      if (!(targetElement instanceof PsiVariable)) {
        return Collections.emptyList();
      }
      Collection<PsiExpression> variableValues = getVariableValues((PsiVariable)targetElement, qualifierExpression);
      if (variableValues.isEmpty()) {
        return DfaPsiUtil.getVariableAssignmentsInFile((PsiVariable)targetElement, false, qualifierExpression);
      }
      return variableValues;
    }
    if (qualifierExpression instanceof PsiLiteralExpression) {
      return Collections.singletonList((PsiLiteralExpression)qualifierExpression);
    }
    return Collections.emptyList();
  }

  public static @NotNull Nullability inferMethodNullability(PsiMethod method) {
    if (PsiUtil.resolveClassInType(method.getReturnType()) == null) {
      return Nullability.UNKNOWN;
    }

    return inferBlockNullability(method, suppressNullable(method));
  }

  private static boolean suppressNullable(PsiMethod method) {
    if (method.getParameterList().isEmpty()) return false;

    for (StandardMethodContract contract : JavaMethodContractUtil.getMethodContracts(method)) {
      if (contract.getReturnValue().isNull()) {
        return true;
      }
    }
    return false;
  }

  public static @NotNull Nullability inferLambdaNullability(PsiLambdaExpression lambda) {
    if (LambdaUtil.getFunctionalInterfaceReturnType(lambda) == null) {
      return Nullability.UNKNOWN;
    }

    return inferBlockNullability(lambda, false);
  }

  private static @NotNull Nullability inferBlockNullability(@NotNull PsiParameterListOwner owner, boolean suppressNullable) {
    PsiElement body = owner.getBody();
    if (body == null) return Nullability.UNKNOWN;

    final DataFlowRunner dfaRunner = new DataFlowRunner(owner.getProject());
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

    return factory.getObjectType(target.getType(), Nullability.NULLABLE);
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
   * (e.g. passed to {@link DataFlowRunner#analyzeMethodRecursively(PsiElement, StandardInstructionVisitor)}) to cover
   * given expression.
   *
   * @param expression expression to cover
   * @return a dataflow context; null if no applicable context found.
   */
  static @Nullable PsiElement getDataflowContext(PsiExpression expression) {
    PsiMember member = PsiTreeUtil.getParentOfType(expression, PsiMember.class);
    while (member instanceof PsiAnonymousClass && PsiTreeUtil.isAncestor(((PsiAnonymousClass)member).getArgumentList(), expression, true)) {
      member = PsiTreeUtil.getParentOfType(member, PsiMember.class);
    }
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
  public static @Nullable Boolean evaluateCondition(@Nullable PsiExpression condition) {
    CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(condition);
    if (result == null) return null;
    return tryCast(ContainerUtil.getOnlyItem(result.getExpressionValues(condition)), Boolean.class);
  }

  public static DfaValue boxUnbox(DfaValue value, @Nullable PsiType type) {
    if (TypeConversionUtil.isPrimitiveWrapper(type)) {
      if (TypeConversionUtil.isPrimitiveAndNotNull(value.getType())) {
        DfaValue boxed = value.getFactory().getBoxedFactory().createBoxed(value, type);
        return boxed == null ? value.getFactory().getUnknown() : boxed;
      }
    }
    if (TypeConversionUtil.isPrimitiveAndNotNull(type)) {
      if (value instanceof DfaBoxedValue || TypeConversionUtil.isPrimitiveWrapper(value.getType())) {
        return SpecialField.UNBOX.createValue(value.getFactory(), value);
      }
      if (value.getDfType() instanceof DfReferenceType) {
        return value.getFactory().getObjectType(type, Nullability.NOT_NULL);
      }
    }
    return value;
  }

  public static @NotNull List<? extends MethodContract> addRangeContracts(@Nullable PsiMethod method,
                                                                          @NotNull List<? extends MethodContract> contracts) {
    if (method == null) return contracts;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    List<MethodContract> rangeContracts = new ArrayList<>();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      LongRangeSet fromType = LongRangeSet.fromType(parameter.getType());
      if (fromType == null) continue;
      LongRangeSet fromAnnotation = LongRangeSet.fromPsiElement(parameter);
      if (fromAnnotation.min() > fromType.min()) {
        MethodContract contract = MethodContract.singleConditionContract(
          ContractValue.argument(i), RelationType.LT, ContractValue.constant(fromAnnotation.min(), parameter.getType()),
          ContractReturnValue.fail());
        rangeContracts.add(contract);
      }
      if (fromAnnotation.max() < fromType.max()) {
        MethodContract contract = MethodContract.singleConditionContract(
          ContractValue.argument(i), RelationType.GT, ContractValue.constant(fromAnnotation.max(), parameter.getType()),
          ContractReturnValue.fail());
        rangeContracts.add(contract);
      }
    }
    return ContainerUtil.concat(rangeContracts, contracts);
  }

  public static boolean isEmptyCollectionConstantField(@Nullable PsiVariable var) {
    if (!(var instanceof PsiField)) return false;
    PsiField field = (PsiField)var;
    return field.getName().startsWith("EMPTY_") && field.getContainingClass() != null &&
           JAVA_UTIL_COLLECTIONS.equals(field.getContainingClass().getQualifiedName());
  }

  public static boolean isNaN(Object value) {
    return value instanceof Double && ((Double)value).isNaN() ||
           value instanceof Float && ((Float)value).isNaN();
  }
}

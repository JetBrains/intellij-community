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
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.LongStreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public class DfaExpressionFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.value.DfaExpressionFactory");
  private static final Condition<String> FALSE_GETTERS = parseFalseGetters();

  private static Condition<String> parseFalseGetters() {
    try {
      String regex = Registry.stringValue("ide.dfa.getters.with.side.effects").trim();
      if (!StringUtil.isEmpty(regex)) {
        final Pattern pattern = Pattern.compile(regex);
        return s -> pattern.matcher(s).matches();
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
    return Conditions.alwaysFalse();
  }

  private final DfaValueFactory myFactory;
  private final Map<Integer, ArrayElementSource> myMockIndices = ContainerUtil.newHashMap();

  DfaExpressionFactory(DfaValueFactory factory) {
    myFactory = factory;
  }

  @Nullable
  public DfaValue getExpressionDfaValue(@Nullable PsiExpression expression) {
    if (expression == null) return null;

    if (expression instanceof PsiParenthesizedExpression) {
      return getExpressionDfaValue(((PsiParenthesizedExpression)expression).getExpression());
    }

    if (expression instanceof PsiArrayAccessExpression) {
      PsiExpression arrayExpression = ((PsiArrayAccessExpression)expression).getArrayExpression();
      DfaVariableValue qualifier = getQualifierVariable(arrayExpression);
      if (qualifier != null) {
        Object index = ExpressionUtils.computeConstantExpression(((PsiArrayAccessExpression)expression).getIndexExpression());
        if (index instanceof Integer) {
          DfaValue arrayElementValue = getArrayElementValue(qualifier, (Integer)index);
          if (arrayElementValue != null) {
            return arrayElementValue;
          }
        }
      }
      PsiType type = expression.getType();
      if (type != null) {
        return myFactory.createTypeValue(type, DfaPsiUtil.getElementNullability(type, null));
      }
    }

    if (expression instanceof PsiMethodCallExpression) {
      return createReferenceValue(((PsiMethodCallExpression)expression).getMethodExpression());
    }

    if (expression instanceof PsiReferenceExpression) {
      return createReferenceValue((PsiReferenceExpression)expression);
    }

    if (expression instanceof PsiLiteralExpression) {
      return myFactory.createLiteralValue((PsiLiteralExpression)expression);
    }

    if (expression instanceof PsiNewExpression || expression instanceof PsiLambdaExpression) {
      return myFactory.createTypeValue(expression.getType(), Nullability.NOT_NULL);
    }

    final Object value = JavaConstantExpressionEvaluator.computeConstantExpression(expression, false);
    if (value != null) {
      PsiType type = expression.getType();
      if (type != null) {
        return myFactory.getConstFactory().createFromValue(value, type, null);
      }
    }

    if (expression instanceof PsiThisExpression || expression instanceof PsiSuperExpression) {
      PsiJavaCodeReferenceElement qualifier = ((PsiQualifiedExpression)expression).getQualifier();
      PsiClass target;
      if (qualifier != null) {
        target = ObjectUtils.tryCast(qualifier.resolve(), PsiClass.class);
      }
      else {
        target = ClassUtils.getContainingClass(expression);
      }
      return target == null
             ? myFactory.createTypeValue(expression.getType(), Nullability.NOT_NULL)
             : myFactory.getVarFactory().createThisValue(target);
    }
    return null;
  }

  private DfaValue createReferenceValue(@NotNull PsiReferenceExpression refExpr) {
    DfaValue specialValue = createFromSpecialField(refExpr);
    if (specialValue != null) {
      return specialValue;
    }
    DfaVariableSource var = getAccessedVariableOrGetter(refExpr.resolve());
    if (var == null) {
      return null;
    }

    PsiModifierListOwner psiElement = var.getPsiElement();
    boolean isVolatile = psiElement != null && psiElement.hasModifierProperty(PsiModifier.VOLATILE);
    if (isVolatile) {
      PsiType type = refExpr.getType();
      return myFactory.createTypeValue(type, DfaPsiUtil.getElementNullability(type, psiElement));
    }
    if (psiElement instanceof PsiVariable && psiElement.hasModifierProperty(PsiModifier.FINAL) && !PsiUtil.isAccessedForWriting(refExpr)) {
      DfaValue constValue = myFactory.getConstFactory().create((PsiVariable)psiElement);
      if (constValue != null && !maybeUninitializedConstant(constValue, refExpr, psiElement)) return constValue;
    }
    if (psiElement instanceof PsiLocalVariable || psiElement instanceof PsiParameter ||
        (psiElement instanceof PsiField &&
         psiElement.hasModifierProperty(PsiModifier.STATIC) &&
         !psiElement.hasModifierProperty(PsiModifier.FINAL)) ||
        isStaticFinalConstantWithoutInitializationHacks(psiElement) ||
        (psiElement instanceof PsiMethod && psiElement.hasModifierProperty(PsiModifier.STATIC))) {
      return myFactory.getVarFactory().createVariableValue(var, refExpr.getType());
    }
    DfaVariableValue qualifier = getQualifierOrThisVariable(refExpr);
    if (qualifier != null) {
      return myFactory.getVarFactory().createVariableValue(var, refExpr.getType(), qualifier);
    }

    PsiType type = refExpr.getType();
    return myFactory.createTypeValue(type, DfaPsiUtil.getElementNullability(type, psiElement));
  }

  /**
   * Returns a DFA variable which represents the qualifier for given reference if possible. For unqualified reference
   * to a non-static member, a variable which represents the corresponding {@code this} may be returned
   *
   * @param refExpr reference to create a qualifier variable for
   * @return a qualifier variable or null if qualifier is unnecessary or cannot be represented as a variable
   */
  @Nullable
  public DfaVariableValue getQualifierOrThisVariable(PsiReferenceExpression refExpr) {
    PsiExpression qualifierExpression = refExpr.getQualifierExpression();
    if (qualifierExpression == null) {
      PsiElement element = refExpr.resolve();
      if (element instanceof PsiMember && !((PsiMember)element).hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass currentClass;
        currentClass = ClassUtils.getContainingClass(refExpr);
        PsiClass memberClass = ((PsiMember)element).getContainingClass();
        if (memberClass != null && currentClass != null) {
          PsiClass target;
          if (currentClass == memberClass || InheritanceUtil.isInheritorOrSelf(currentClass, memberClass, true)) {
            target = currentClass;
          }
          else {
            target = memberClass;
          }
          return myFactory.getVarFactory().createThisValue(target);
        }
      }
    }
    return getQualifierVariable(qualifierExpression);
  }

  @Nullable
  private DfaVariableValue getQualifierVariable(PsiExpression qualifierExpression) {
    DfaValue qualifierValue = getExpressionDfaValue(qualifierExpression);
    DfaVariableValue qualifier = null;
    if (qualifierValue instanceof DfaVariableValue) {
      qualifier = (DfaVariableValue)qualifierValue;
    }
    else if (qualifierValue instanceof DfaConstValue) {
      Object constValue = ((DfaConstValue)qualifierValue).getValue();
      if (constValue instanceof PsiVariable) {
        qualifier = myFactory.getVarFactory().createVariableValue((PsiVariable)constValue);
      }
    }
    return qualifier;
  }

  private static boolean maybeUninitializedConstant(DfaValue constValue,
                                                    @NotNull PsiReferenceExpression refExpr,
                                                    PsiModifierListOwner var) {
    // If static final field is referred from the same or inner/nested class,
    // we consider that it might be uninitialized yet as some class initializers may call its methods or
    // even instantiate objects of this class and call their methods
    if(!(constValue instanceof DfaConstValue) || ((DfaConstValue)constValue).getValue() != var) return false;
    if(!(var instanceof PsiField) || var instanceof PsiEnumConstant) return false;
    return PsiTreeUtil.getTopmostParentOfType(refExpr, PsiClass.class) == PsiTreeUtil.getTopmostParentOfType(var, PsiClass.class);
  }

  private static boolean isStaticFinalConstantWithoutInitializationHacks(PsiModifierListOwner var) {
    return (var instanceof PsiField && var.hasModifierProperty(PsiModifier.FINAL) && var.hasModifierProperty(PsiModifier.STATIC)) &&
           !DfaUtil.hasInitializationHacks((PsiField)var);
  }

  @Nullable
  private DfaValue createFromSpecialField(PsiReferenceExpression refExpr) {
    PsiElement target = refExpr.resolve();
    if (!(target instanceof PsiModifierListOwner)) return null;
    SpecialField sf = SpecialField.findSpecialField(target);
    if (sf == null) return null;
    DfaVariableValue qualifier = getQualifierOrThisVariable(refExpr);
    if (qualifier == null) return null;
    return sf.createValue(myFactory, qualifier);
  }

  @Contract("null -> null")
  @Nullable
  public static DfaVariableSource getAccessedVariableOrGetter(final PsiElement target) {
    if (target instanceof PsiVariable) {
      return new PlainSource((PsiVariable)target);
    }
    if (target instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)target;
      if (PropertyUtilBase.isSimplePropertyGetter(method) && isContractAllowedForGetter(method)) {
        String qName = PsiUtil.getMemberQualifiedName(method);
        if (qName == null || !FALSE_GETTERS.value(qName)) {
          return new GetterSource(method);
        }
      }
      if (method.getParameterList().isEmpty()) {
        if ((JavaMethodContractUtil.isPure(method) ||
            AnnotationUtil.findAnnotation(method.getContainingClass(), "javax.annotation.concurrent.Immutable") != null) &&
            isContractAllowedForGetter(method)) {
          return new GetterSource(method);
        }
      }
    }
    return null;
  }

  private static boolean isContractAllowedForGetter(PsiMethod method) {
    List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, null);
    if (contracts.size() == 1) {
      MethodContract contract = contracts.get(0);
      return contract.isTrivial() && contract.getReturnValue().equals(ContractReturnValue.returnNew());
    }
    return contracts.isEmpty();
  }

  @NotNull
  private DfaValue getAdvancedExpressionDfaValue(@Nullable PsiExpression expression) {
    if (expression == null) return DfaUnknownValue.getInstance();
    DfaValue value = getExpressionDfaValue(expression);
    if (value != null) {
      return value;
    }
    if (expression instanceof PsiConditionalExpression) {
      return getAdvancedExpressionDfaValue(((PsiConditionalExpression)expression).getThenExpression()).union(
        getAdvancedExpressionDfaValue(((PsiConditionalExpression)expression).getElseExpression()));
    }
    PsiType type = expression.getType();
    if (type instanceof PsiPrimitiveType) return DfaUnknownValue.getInstance();
    return myFactory.createTypeValue(type, NullabilityUtil.getExpressionNullability(expression));
  }

  @NotNull
  public DfaValue getArrayElementValue(DfaValue array, LongRangeSet indexSet) {
    if (!(array instanceof DfaVariableValue)) return DfaUnknownValue.getInstance();
    if (indexSet.isEmpty()) return DfaUnknownValue.getInstance();
    long min = indexSet.min();
    long max = indexSet.max();
    if (min == max && min >= 0 && min < Integer.MAX_VALUE) {
      DfaValue value = getArrayElementValue(array, (int)min);
      return value == null ? DfaUnknownValue.getInstance() : value;
    }
    DfaVariableValue arrayDfaVar = (DfaVariableValue)array;
    PsiModifierListOwner arrayPsiVar = arrayDfaVar.getPsiVariable();
    if (!(arrayPsiVar instanceof PsiVariable)) return DfaUnknownValue.getInstance();
    PsiExpression[] elements = ExpressionUtils.getConstantArrayElements((PsiVariable)arrayPsiVar);
    if (elements == null || elements.length == 0) return DfaUnknownValue.getInstance();
    indexSet = indexSet.intersect(LongRangeSet.range(0, elements.length - 1));
    if (indexSet.isEmpty() || indexSet.max() - indexSet.min() > 100) return DfaUnknownValue.getInstance();
    return LongStreamEx.of(indexSet.stream())
                .mapToObj(idx -> getAdvancedExpressionDfaValue(elements[(int)idx]))
                .prefix(DfaValue::union)
                .takeWhileInclusive(value -> value != DfaUnknownValue.getInstance())
                .reduce((a, b) -> b)
                .orElse(DfaUnknownValue.getInstance());
  }

  @Contract("null, _ -> null")
  @Nullable
  public DfaValue getArrayElementValue(DfaValue array, int index) {
    if (!(array instanceof DfaVariableValue)) return null;
    DfaVariableValue arrayDfaVar = (DfaVariableValue)array;
    PsiType type = arrayDfaVar.getVariableType();
    if (!(type instanceof PsiArrayType)) return null;
    PsiType componentType = ((PsiArrayType)type).getComponentType();
    PsiModifierListOwner arrayPsiVar = arrayDfaVar.getPsiVariable();
    if (arrayPsiVar instanceof PsiVariable) {
      PsiExpression constantArrayElement = ExpressionUtils.getConstantArrayElement((PsiVariable)arrayPsiVar, index);
      if (constantArrayElement != null) {
        return getAdvancedExpressionDfaValue(constantArrayElement);
      }
    }
    ArrayElementSource indexVariable = getArrayIndexVariable(index);
    if (indexVariable == null) return null;
    return myFactory.getVarFactory().createVariableValue(indexVariable, componentType, arrayDfaVar);
  }

  @Nullable
  private ArrayElementSource getArrayIndexVariable(int index) {
    if (index >= 0) {
      return myMockIndices.computeIfAbsent(index, ArrayElementSource::new);
    }
    return null;
  }

  static final class PlainSource implements DfaVariableSource {
    private final @NotNull PsiVariable myVariable;

    PlainSource(@NotNull PsiVariable variable) {
      myVariable = variable;
    }

    @NotNull
    @Override
    public String toString() {
      return String.valueOf(myVariable.getName());
    }

    @Override
    public PsiVariable getPsiElement() {
      return myVariable;
    }

    @Override
    public boolean isStable() {
      return myVariable instanceof PsiLocalVariable ||
             myVariable instanceof PsiParameter ||
             myVariable.hasModifierProperty(PsiModifier.FINAL);
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || obj instanceof PlainSource && ((PlainSource)obj).myVariable == myVariable;
    }
  }

  private static final class GetterSource implements DfaVariableSource {
    private final @NotNull PsiMethod myGetter;

    GetterSource(@NotNull PsiMethod getter) {
      myGetter = getter;
    }

    @NotNull
    @Override
    public String toString() {
      return myGetter.getName();
    }

    @NotNull
    @Override
    public PsiMethod getPsiElement() {
      return myGetter;
    }

    @Override
    public boolean isStable() {
      return false;
    }

    @Override
    public boolean isCall() {
      return true;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || (obj instanceof GetterSource && ((GetterSource)obj).myGetter == myGetter);
    }
  }

  private static final class ArrayElementSource implements DfaVariableSource {
    private final int myIndex;

    ArrayElementSource(int index) {
      myIndex = index;
    }

    @NotNull
    @Override
    public String toString() {
      return "[" + myIndex + "]";
    }

    @Override
    public boolean isStable() {
      return false;
    }
  }

  public static final class ThisSource implements DfaVariableSource {
    @NotNull
    private final PsiClass myQualifier;

    ThisSource(@NotNull PsiClass qualifier) {
      myQualifier = qualifier;
    }

    @NotNull
    @Override
    public String toString() {
      return myQualifier.getName() + ".this";
    }

    @Override
    public PsiClass getPsiElement() {
      return myQualifier;
    }

    @Override
    public boolean isStable() {
      return true;
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj || obj instanceof ThisSource && ((ThisSource)obj).myQualifier == myQualifier;
    }
  }
}

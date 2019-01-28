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
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.util.*;
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
import java.util.Objects;
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
  private final Map<Integer, ArrayElementDescriptor> myArrayIndices = ContainerUtil.newHashMap();

  DfaExpressionFactory(DfaValueFactory factory) {
    myFactory = factory;
  }

  @Nullable
  @Contract("null -> null")
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
        return myFactory.getConstFactory().createFromValue(value, type);
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
    PsiElement target = refExpr.resolve();
    if (target instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)target;
      if (variable.hasModifierProperty(PsiModifier.FINAL) && !PsiUtil.isAccessedForWriting(refExpr)) {
        DfaValue constValue = myFactory.getConstFactory().create(variable);
        if (constValue != null && !maybeUninitializedConstant(constValue, refExpr, variable)) return constValue;
      }
    }
    VariableDescriptor var = getAccessedVariableOrGetter(target);
    if (var == null) {
      return null;
    }

    DfaVariableValue qualifier = getQualifierOrThisVariable(refExpr);
    return var.createValue(myFactory, qualifier, true);
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

  @Contract("null -> null")
  @Nullable
  public static VariableDescriptor getAccessedVariableOrGetter(final PsiElement target) {
    SpecialField sf = SpecialField.findSpecialField(target);
    if (sf != null) {
      return sf;
    }
    if (target instanceof PsiVariable) {
      return new PlainDescriptor((PsiVariable)target);
    }
    if (target instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)target;
      if (PropertyUtilBase.isSimplePropertyGetter(method) && isContractAllowedForGetter(method)) {
        String qName = PsiUtil.getMemberQualifiedName(method);
        if (qName == null || !FALSE_GETTERS.value(qName)) {
          return new GetterDescriptor(method);
        }
      }
      if (method.getParameterList().isEmpty()) {
        if ((JavaMethodContractUtil.isPure(method) ||
            AnnotationUtil.findAnnotation(method.getContainingClass(), "javax.annotation.concurrent.Immutable") != null) &&
            isContractAllowedForGetter(method)) {
          return new GetterDescriptor(method);
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
  private DfaValue getAdvancedExpressionDfaValue(@Nullable PsiExpression expression, @Nullable PsiType targetType) {
    if (expression == null) return DfaUnknownValue.getInstance();
    DfaValue value = getExpressionDfaValue(expression);
    if (value != null) {
      return DfaUtil.boxUnbox(value, targetType);
    }
    if (expression instanceof PsiConditionalExpression) {
      return getAdvancedExpressionDfaValue(((PsiConditionalExpression)expression).getThenExpression(), targetType).unite(
        getAdvancedExpressionDfaValue(((PsiConditionalExpression)expression).getElseExpression(), targetType));
    }
    PsiType type = expression.getType();
    if (type instanceof PsiPrimitiveType) return DfaUnknownValue.getInstance();
    DfaValue typeValue = myFactory.createTypeValue(type, NullabilityUtil.getExpressionNullability(expression));
    if (expression instanceof PsiArrayInitializerExpression) {
      int length = ((PsiArrayInitializerExpression)expression).getInitializers().length;
      return myFactory.withFact(typeValue, DfaFactType.SPECIAL_FIELD_VALUE, SpecialField.ARRAY_LENGTH.withValue(myFactory.getInt(length)));
    }
    return DfaUtil.boxUnbox(typeValue, targetType);
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
    PsiType arrayType = ((PsiVariable)arrayPsiVar).getType();
    PsiType targetType = arrayType instanceof PsiArrayType ? ((PsiArrayType)arrayType).getComponentType() : null;
    PsiExpression[] elements = ExpressionUtils.getConstantArrayElements((PsiVariable)arrayPsiVar);
    if (elements == null || elements.length == 0) return DfaUnknownValue.getInstance();
    indexSet = indexSet.intersect(LongRangeSet.range(0, elements.length - 1));
    if (indexSet.isEmpty() || indexSet.max() - indexSet.min() > 100) return DfaUnknownValue.getInstance();
    return LongStreamEx.of(indexSet.stream())
                .mapToObj(idx -> getAdvancedExpressionDfaValue(elements[(int)idx], targetType))
                .prefix(DfaValue::unite)
                .takeWhileInclusive(value -> value != DfaUnknownValue.getInstance())
                .reduce((a, b) -> b)
                .orElse(DfaUnknownValue.getInstance());
  }

  @Contract("null, _ -> null")
  @Nullable
  public DfaValue getArrayElementValue(DfaValue array, int index) {
    if (!(array instanceof DfaVariableValue)) return null;
    DfaVariableValue arrayDfaVar = (DfaVariableValue)array;
    PsiType type = arrayDfaVar.getType();
    if (!(type instanceof PsiArrayType)) return null;
    PsiModifierListOwner arrayPsiVar = arrayDfaVar.getPsiVariable();
    if (arrayPsiVar instanceof PsiVariable) {
      PsiExpression constantArrayElement = ExpressionUtils.getConstantArrayElement((PsiVariable)arrayPsiVar, index);
      if (constantArrayElement != null) {
        return getAdvancedExpressionDfaValue(constantArrayElement, ((PsiArrayType)type).getComponentType());
      }
    }
    ArrayElementDescriptor indexVariable = getArrayIndexVariable(index);
    if (indexVariable == null) return null;
    return indexVariable.createValue(myFactory, arrayDfaVar);
  }

  @Nullable
  private ArrayElementDescriptor getArrayIndexVariable(int index) {
    if (index >= 0) {
      return myArrayIndices.computeIfAbsent(index, ArrayElementDescriptor::new);
    }
    return null;
  }

  @NotNull
  private static PsiSubstitutor getSubstitutor(PsiElement member, @Nullable DfaVariableValue qualifier) {
    if (member instanceof PsiMember && qualifier != null) {
      PsiClass fieldClass = ((PsiMember)member).getContainingClass();
      PsiClassType classType = ObjectUtils.tryCast(qualifier.getType(), PsiClassType.class);
      if (classType != null && InheritanceUtil.isInheritorOrSelf(classType.resolve(), fieldClass, true)) {
        return TypeConversionUtil.getSuperClassSubstitutor(fieldClass, classType);
      }
    }
    return PsiSubstitutor.EMPTY;
  }

  static final class PlainDescriptor implements VariableDescriptor {
    private final @NotNull PsiVariable myVariable;

    PlainDescriptor(@NotNull PsiVariable variable) {
      myVariable = variable;
    }

    @NotNull
    @Override
    public String toString() {
      return String.valueOf(myVariable.getName());
    }

    @Override
    public PsiType getType(@Nullable DfaVariableValue qualifier) {
      PsiType type = myVariable.getType();
      if (type instanceof PsiEllipsisType) {
        type = ((PsiEllipsisType)type).toArrayType();
      }
      return getSubstitutor(myVariable, qualifier).substitute(type);
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

    @NotNull
    @Override
    public DfaValue createValue(@NotNull DfaValueFactory factory, @Nullable DfaValue qualifier, boolean forAccessor) {
      if (myVariable.getType().equalsToText(CommonClassNames.JAVA_LANG_VOID)) {
        return factory.getConstFactory().getNull();
      }
      if (myVariable.hasModifierProperty(PsiModifier.VOLATILE)) {
        PsiType type = getType(ObjectUtils.tryCast(qualifier, DfaVariableValue.class));
        return factory.createTypeValue(type, DfaPsiUtil.getElementNullability(type, myVariable));
      }
      if (myVariable instanceof PsiLocalVariable || myVariable instanceof PsiParameter ||
          (myVariable instanceof PsiField && myVariable.hasModifierProperty(PsiModifier.STATIC) &&
           (!myVariable.hasModifierProperty(PsiModifier.FINAL) || !DfaUtil.hasInitializationHacks((PsiField)myVariable)))) {
        return factory.getVarFactory().createVariableValue(this);
      }
      return VariableDescriptor.super.createValue(factory, qualifier, forAccessor);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myVariable.getName());
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || obj instanceof PlainDescriptor && ((PlainDescriptor)obj).myVariable == myVariable;
    }
  }

  private static final class GetterDescriptor implements VariableDescriptor {
    private final @NotNull PsiMethod myGetter;
    private final boolean myStable;

    GetterDescriptor(@NotNull PsiMethod getter) {
      myGetter = getter;
      PsiField field = PsiUtil.canBeOverridden(getter) ? null : PropertyUtil.getFieldOfGetter(getter);
      myStable = field != null && field.hasModifierProperty(PsiModifier.FINAL);
    }

    @NotNull
    @Override
    public String toString() {
      return myGetter.getName();
    }

    @Nullable
    @Override
    public PsiType getType(@Nullable DfaVariableValue qualifier) {
      return getSubstitutor(myGetter, qualifier).substitute(myGetter.getReturnType());
    }

    @NotNull
    @Override
    public PsiMethod getPsiElement() {
      return myGetter;
    }

    @Override
    public boolean isStable() {
      return myStable;
    }

    @Override
    public boolean isCall() {
      return true;
    }

    @NotNull
    @Override
    public DfaValue createValue(@NotNull DfaValueFactory factory, @Nullable DfaValue qualifier, boolean forAccessor) {
      if (myGetter.hasModifierProperty(PsiModifier.STATIC)) {
        return factory.getVarFactory().createVariableValue(this);
      }
      return VariableDescriptor.super.createValue(factory, qualifier, forAccessor);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myGetter.getName());
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || (obj instanceof GetterDescriptor && ((GetterDescriptor)obj).myGetter == myGetter);
    }
  }

  private static final class ArrayElementDescriptor implements VariableDescriptor {
    private final int myIndex;

    ArrayElementDescriptor(int index) {
      myIndex = index;
    }

    @Nullable
    @Override
    public PsiType getType(@Nullable DfaVariableValue qualifier) {
      if (qualifier == null) return null;
      PsiType qualifierType = qualifier.getType();
      return qualifierType instanceof PsiArrayType ? ((PsiArrayType)qualifierType).getComponentType() : null;
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

  public static final class ThisDescriptor implements VariableDescriptor {
    @NotNull
    private final PsiClass myQualifier;

    ThisDescriptor(@NotNull PsiClass qualifier) {
      myQualifier = qualifier;
    }

    @NotNull
    @Override
    public String toString() {
      return myQualifier.getName() + ".this";
    }

    @NotNull
    @Override
    public PsiType getType(@Nullable DfaVariableValue qualifier) {
      return new PsiImmediateClassType(myQualifier, PsiSubstitutor.EMPTY);
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
    public int hashCode() {
      return Objects.hashCode(myQualifier.getQualifiedName());
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj || obj instanceof ThisDescriptor && ((ThisDescriptor)obj).myQualifier == myQualifier;
    }
  }
}

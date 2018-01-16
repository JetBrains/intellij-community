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
import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.impl.light.LightVariableBuilder;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private final Map<Integer, PsiVariable> myMockIndices = ContainerUtil.newHashMap();

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
      return myFactory.createTypeValue(expression.getType(), Nullness.NOT_NULL);
    }

    final Object value = JavaConstantExpressionEvaluator.computeConstantExpression(expression, false);
    if (value != null) {
      PsiType type = expression.getType();
      if (type != null) {
        return myFactory.getConstFactory().createFromValue(value, type, null);
      }
    }

    if (expression instanceof PsiThisExpression) {
      PsiJavaCodeReferenceElement qualifier = ((PsiThisExpression)expression).getQualifier();
      PsiElement target = qualifier == null ? null : qualifier.resolve();
      if (target instanceof PsiClass) {
        return myFactory.getVarFactory().createVariableValue((PsiModifierListOwner)target, null, false, null);
      }
    }

    return null;
  }

  private DfaValue createReferenceValue(@NotNull PsiReferenceExpression refExpr) {
    DfaValue specialValue = createFromSpecialField(refExpr);
    if (specialValue != null) {
      return specialValue;
    }
    PsiModifierListOwner var = getAccessedVariableOrGetter(refExpr.resolve());
    if (var == null) {
      return null;
    }

    if (!var.hasModifierProperty(PsiModifier.VOLATILE)) {
      if (var instanceof PsiVariable && var.hasModifierProperty(PsiModifier.FINAL) && !PsiUtil.isAccessedForWriting(refExpr)) {
        DfaValue constValue = myFactory.getConstFactory().create((PsiVariable)var);
        if (constValue != null && !maybeUninitializedConstant(constValue, refExpr, var)) return constValue;
      }

      if (ExpressionUtil.isEffectivelyUnqualified(refExpr) || isStaticFinalConstantWithoutInitializationHacks(var) ||
          (var instanceof PsiMethod && var.hasModifierProperty(PsiModifier.STATIC))) {
        return myFactory.getVarFactory().createVariableValue(var, refExpr.getType(), false, null);
      }

      DfaVariableValue qualifier = getQualifierVariable(refExpr.getQualifierExpression());
      if (qualifier != null) {
        return myFactory.getVarFactory().createVariableValue(var, refExpr.getType(), false, qualifier);
      }
    }

    PsiType type = refExpr.getType();
    return myFactory.createTypeValue(type, DfaPsiUtil.getElementNullability(type, var));
  }

  private DfaVariableValue getQualifierVariable(PsiExpression qualifierExpression) {
    DfaValue qualifierValue = getExpressionDfaValue(qualifierExpression);
    DfaVariableValue qualifier = null;
    if (qualifierValue instanceof DfaVariableValue) {
      qualifier = (DfaVariableValue)qualifierValue;
    }
    else if (qualifierValue instanceof DfaConstValue) {
      Object constValue = ((DfaConstValue)qualifierValue).getValue();
      if (constValue instanceof PsiVariable) {
        qualifier = myFactory.getVarFactory().createVariableValue((PsiVariable)constValue, false);
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
    if (!(target instanceof PsiModifierListOwner)) {
      return null;
    }
    for (SpecialField sf : SpecialField.values()) {
      if (sf.isMyAccessor((PsiModifierListOwner)target)) {
        DfaVariableValue qualifier = getQualifierVariable(refExpr.getQualifierExpression());
        if (qualifier != null) {
          return sf.createValue(myFactory, qualifier);
        }
      }
    }
    return null;
  }

  @Contract("null -> null")
  @Nullable
  public static PsiModifierListOwner getAccessedVariableOrGetter(final PsiElement target) {
    if (target instanceof PsiVariable) {
      return (PsiVariable)target;
    }
    if (target instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)target;
      if (PropertyUtilBase.isSimplePropertyGetter(method) && ControlFlowAnalyzer.getMethodCallContracts(method, null).isEmpty()) {
        String qName = PsiUtil.getMemberQualifiedName(method);
        if (qName == null || !FALSE_GETTERS.value(qName)) {
          return method;
        }
      }
      if (method.getParameterList().getParametersCount() == 0) {
        if ((ControlFlowAnalyzer.isPure(method) ||
            AnnotationUtil.findAnnotation(method.getContainingClass(), "javax.annotation.concurrent.Immutable") != null) &&
            ControlFlowAnalyzer.getMethodCallContracts(method, null).isEmpty()) {
          return method;
        }
      }
    }
    return null;
  }

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
        return getExpressionDfaValue(constantArrayElement);
      }
    }
    PsiVariable indexVariable = getArrayIndexVariable(arrayPsiVar, index);
    if (indexVariable == null) return null;
    return myFactory.getVarFactory().createVariableValue(indexVariable, componentType, false, arrayDfaVar);
  }

  @Nullable
  private PsiVariable getArrayIndexVariable(@NotNull PsiElement anchor, int index) {
    if (index >= 0) {
      return myMockIndices.computeIfAbsent(index, k -> new LightVariableBuilder<>("$array$index$" + k, PsiType.INT, anchor));
    }
    return null;
  }
}

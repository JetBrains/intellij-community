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
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.impl.light.LightVariableBuilder;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
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

  public DfaExpressionFactory(DfaValueFactory factory) {
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
      DfaValue qualifier = getExpressionDfaValue(arrayExpression);
      if (qualifier instanceof DfaVariableValue) {
        PsiVariable indexVar = getArrayIndexVariable(((PsiArrayAccessExpression)expression).getIndexExpression());
        if (indexVar != null) {
          return myFactory.getVarFactory().createVariableValue(indexVar, expression.getType(), false, (DfaVariableValue)qualifier);
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
    PsiModifierListOwner var = getAccessedVariableOrGetter(refExpr.resolve());
    if (var == null) {
      return null;
    }

    if (!var.hasModifierProperty(PsiModifier.VOLATILE)) {
      if (var instanceof PsiVariable && var.hasModifierProperty(PsiModifier.FINAL) && !PsiUtil.isAccessedForWriting(refExpr)) {
        DfaValue constValue = myFactory.getConstFactory().create((PsiVariable)var);
        if (constValue != null) return constValue;
      }

      if (DfaValueFactory.isEffectivelyUnqualified(refExpr) || isStaticFinalConstantWithoutInitializationHacks(var)) {
        return myFactory.getVarFactory().createVariableValue(var, refExpr.getType(), false, null);
      }

      DfaValue qualifierValue = getExpressionDfaValue(refExpr.getQualifierExpression());
      if (qualifierValue instanceof DfaVariableValue) {
        return myFactory.getVarFactory().createVariableValue(var, refExpr.getType(), false, (DfaVariableValue)qualifierValue);
      }
    }

    PsiType type = refExpr.getType();
    return myFactory.createTypeValue(type, DfaPsiUtil.getElementNullability(type, var));
  }

  private static boolean isStaticFinalConstantWithoutInitializationHacks(PsiModifierListOwner var) {
    return (var instanceof PsiField && var.hasModifierProperty(PsiModifier.FINAL) && var.hasModifierProperty(PsiModifier.STATIC)) &&
           !DfaUtil.hasInitializationHacks((PsiField)var);
  }

  @Nullable
  private static PsiModifierListOwner getAccessedVariableOrGetter(final PsiElement target) {
    if (target instanceof PsiVariable) {
      return (PsiVariable)target;
    }
    if (target instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)target;
      if (PropertyUtil.isSimplePropertyGetter(method) && !(method.getReturnType() instanceof PsiPrimitiveType)) {
        String qName = PsiUtil.getMemberQualifiedName(method);
        if (qName == null || !FALSE_GETTERS.value(qName)) {
          return method;
        }
      }
      for (SpecialField sf : SpecialField.values()) {
        if (sf.isMyAccessor(method)) {
          return sf.getCanonicalOwner(null, ((PsiMethod)target).getContainingClass());
        }
      }
      if (AnnotationUtil.findAnnotation(method.getContainingClass(), "javax.annotation.concurrent.Immutable") != null) {
        return method;
      }
    }
    return null;
  }

  @Nullable
  private PsiVariable getArrayIndexVariable(@Nullable PsiExpression indexExpression) {
    Object constant = JavaConstantExpressionEvaluator.computeConstantExpression(indexExpression, false);
    if (constant instanceof Integer && ((Integer)constant).intValue() >= 0) {
      return myMockIndices
        .computeIfAbsent((Integer)constant, k -> new LightVariableBuilder<>("$array$index$" + k, PsiType.INT, indexExpression));
    }
    return null;
  }
}

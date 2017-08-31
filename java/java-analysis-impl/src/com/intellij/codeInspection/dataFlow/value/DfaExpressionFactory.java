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
import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.impl.light.LightVariableBuilder;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
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
        if (!PsiUtil.isAccessedForWriting(refExpr) &&
            isFieldDereferenceBeforeInitialization(refExpr) &&
            !(refExpr.getType() instanceof PsiPrimitiveType)) {
          return myFactory.getConstFactory().getNull();
        }

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
    if (var instanceof PsiField && var.hasModifierProperty(PsiModifier.FINAL) && var.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass containingClass = ((PsiField)var).getContainingClass();
      if (containingClass != null && !System.class.getName().equals(containingClass.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isFieldDereferenceBeforeInitialization(PsiReferenceExpression ref) {
    PsiMember placeMember = PsiTreeUtil.getParentOfType(ref, PsiMember.class, true, PsiClass.class, PsiLambdaExpression.class);
    if (placeMember == null) return false;

    PsiClass placeClass = placeMember.getContainingClass();
    PsiElement target = ref.resolve();
    if (target instanceof PsiField) {
      PsiField targetField = (PsiField)target;
      if (placeClass != null && placeClass == targetField.getContainingClass()) {
        if (!placeMember.hasModifier(JvmModifier.STATIC) && targetField.hasModifier(JvmModifier.STATIC)) {
          return false;
        }

        return getAccessOffset(placeMember) < getWriteOffset(targetField);
      }
    }
    return false;
  }

  private static int getWriteOffset(PsiField target) {
    // Final field: written either in field initializer or in class initializer block which directly writes this field
    // Non-final field: written either in field initializer, in class initializer which directly writes this field or calls any method,
    //    or in other field initializer which directly writes this field or calls any method
    boolean isFinal = target.hasModifier(JvmModifier.FINAL);
    int offset = Integer.MAX_VALUE;
    if (target.getInitializer() != null) {
      offset = target.getInitializer().getTextOffset();
      if (isFinal) return offset;
    }
    PsiClass aClass = Objects.requireNonNull(target.getContainingClass());
    PsiClassInitializer[] initializers = aClass.getInitializers();
    Predicate<PsiElement> writesToTarget = element -> !ReferencesSearch.search(target, new LocalSearchScope(element))
      .forEach(e -> !(e instanceof PsiExpression) || !PsiUtil.isAccessedForWriting((PsiExpression)e));
    Predicate<PsiElement> hasSideEffectCall = element -> !PsiTreeUtil.findChildrenOfType(element, PsiMethodCallExpression.class).stream()
      .map(PsiMethodCallExpression::resolveMethod).allMatch(method -> method != null && ControlFlowAnalyzer.isPure(method));
    for (PsiClassInitializer initializer : initializers) {
      if (initializer.hasModifier(JvmModifier.STATIC) != target.hasModifier(JvmModifier.STATIC)) continue;
      if (!isFinal && hasSideEffectCall.test(initializer)) {
        // non-final field could be written indirectly (via method call), so assume it's written in the first applicable initializer
        offset = Math.min(offset, initializer.getTextOffset());
        break;
      }
      if (writesToTarget.test(initializer)) {
        offset = Math.min(offset, initializer.getTextOffset());
        if (isFinal) return offset;
        break;
      }
    }
    if (!isFinal) {
      for (PsiField field : aClass.getFields()) {
        if (field.hasModifier(JvmModifier.STATIC) != target.hasModifier(JvmModifier.STATIC)) continue;
        if (PsiTreeUtil.findChildOfType(field.getInitializer(), PsiCall.class) != null || writesToTarget.test(field)) {
          offset = Math.min(offset, field.getTextOffset());
          break;
        }
      }
    }
    return offset;
  }

  private static int getAccessOffset(PsiMember referrer) {
    if (referrer instanceof PsiField) {
      return referrer.getTextOffset();
    }
    PsiClass aClass = Objects.requireNonNull(referrer.getContainingClass());
    if (referrer instanceof PsiMethod) {
      boolean isStatic = referrer.hasModifier(JvmModifier.STATIC);
      for (PsiField field : aClass.getFields()) {
        if (field.hasModifier(JvmModifier.STATIC) != isStatic) continue;
        PsiExpression initializer = field.getInitializer();
        Predicate<PsiExpression> callToMethod = (PsiExpression e) -> {
          if (!(e instanceof PsiMethodCallExpression)) return false;
          PsiMethodCallExpression call = (PsiMethodCallExpression)e;
          return call.getMethodExpression().isReferenceTo(referrer) &&
                 (isStatic || DfaValueFactory.isEffectivelyUnqualified(call.getMethodExpression()));
        };
        if (ExpressionUtils.isMatchingChildAlwaysExecuted(initializer, callToMethod)) {
          // current method is definitely called from some field initialization
          return field.getTextOffset();
        }
      }
    }
    return Integer.MAX_VALUE; // accessed after initialization or at unknown moment
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

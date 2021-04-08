// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ConcurrencyAnnotationsManager;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ArrayElementDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.GetterDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ThisDescriptor;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DfaExpressionFactory {

   private DfaExpressionFactory() {
   }

  @Nullable
  @Contract("_, null -> null")
  public static DfaValue getExpressionDfaValue(@NotNull DfaValueFactory factory, @Nullable PsiExpression expression) {
    if (expression == null) return null;

    if (expression instanceof PsiParenthesizedExpression) {
      return getExpressionDfaValue(factory, ((PsiParenthesizedExpression)expression).getExpression());
    }

    if (expression instanceof PsiArrayAccessExpression) {
      PsiExpression arrayExpression = ((PsiArrayAccessExpression)expression).getArrayExpression();
      DfaValue qualifier = getQualifierValue(factory, arrayExpression);
      if (qualifier != null) {
        Object index = ExpressionUtils.computeConstantExpression(((PsiArrayAccessExpression)expression).getIndexExpression());
        if (index instanceof Integer) {
          DfaValue arrayElementValue = ArrayElementDescriptor.getArrayElementValue(factory, qualifier, (Integer)index);
          if (arrayElementValue != null) {
            return arrayElementValue;
          }
        }
      }
      PsiType type = expression.getType();
      if (type != null) {
        return factory.getObjectType(type, DfaPsiUtil.getElementNullability(type, null));
      }
    }

    if (expression instanceof PsiMethodCallExpression) {
      return createReferenceValue(factory, ((PsiMethodCallExpression)expression).getMethodExpression());
    }

    if (expression instanceof PsiReferenceExpression) {
      return createReferenceValue(factory, (PsiReferenceExpression)expression);
    }

    if (expression instanceof PsiLiteralExpression) {
      return factory.fromDfType(DfaPsiUtil.fromLiteral((PsiLiteralExpression)expression));
    }

    if (expression instanceof PsiNewExpression || expression instanceof PsiLambdaExpression) {
      return factory.getObjectType(expression.getType(), Nullability.NOT_NULL);
    }

    final Object value = JavaConstantExpressionEvaluator.computeConstantExpression(expression, false);
    if (value != null) {
      PsiType type = expression.getType();
      if (type != null) {
        return factory.getConstant(value, type);
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
             ? factory.getObjectType(expression.getType(), Nullability.NOT_NULL)
             : ThisDescriptor.createThisValue(factory, target);
    }
    return null;
  }

  private static DfaValue createReferenceValue(DfaValueFactory factory, @NotNull PsiReferenceExpression refExpr) {
    PsiElement target = refExpr.resolve();
    if (target instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)target;
      if (!PsiUtil.isAccessedForWriting(refExpr)) {
        DfaValue constValue = factory.getConstantFromVariable(variable);
        if (constValue != null && !maybeUninitializedConstant(constValue, refExpr, variable)) return constValue;
      }
    }
    VariableDescriptor var = getAccessedVariableOrGetter(target);
    if (var == null) {
      return null;
    }

    DfaValue qualifier = getQualifierOrThisValue(factory, refExpr);
    DfaValue result = var.createValue(factory, qualifier, true);
    if (var instanceof SpecialField) {
      PsiType wantedType = refExpr.getType();
      result = DfaUtil.boxUnbox(result, wantedType);
    }
    return result;
  }

  /**
   * Returns a DFA variable which represents the qualifier for given reference if possible. For unqualified reference
   * to a non-static member, a variable which represents the corresponding {@code this} may be returned
   *
   * @param factory factory to create new expressions
   * @param refExpr reference to create a qualifier variable for
   * @return a qualifier variable or null if qualifier is unnecessary or cannot be represented as a variable
   */
  @Nullable
  public static DfaValue getQualifierOrThisValue(DfaValueFactory factory, PsiReferenceExpression refExpr) {
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
          return ThisDescriptor.createThisValue(factory, target);
        }
      }
    }
    return getQualifierValue(factory, qualifierExpression);
  }

  @Nullable
  private static DfaValue getQualifierValue(DfaValueFactory factory, PsiExpression qualifierExpression) {
    DfaValue qualifierValue = getExpressionDfaValue(factory, qualifierExpression);
    if (qualifierValue == null) return null;
    PsiVariable constVar = qualifierValue.getDfType().getConstantOfType(PsiVariable.class);
    if (constVar != null) {
      return PlainDescriptor.createVariableValue(factory, constVar);
    }
    return qualifierValue;
  }

  private static boolean maybeUninitializedConstant(DfaValue constValue,
                                                    @NotNull PsiReferenceExpression refExpr,
                                                    PsiModifierListOwner var) {
    // If static final field is referred from the same or inner/nested class,
    // we consider that it might be uninitialized yet as some class initializers may call its methods or
    // even instantiate objects of this class and call their methods
    if (!constValue.getDfType().isConst(var)) return false;
    if (!(var instanceof PsiField) || var instanceof PsiEnumConstant) return false;
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
      if (method.getParameterList().isEmpty() &&
          (PropertyUtilBase.isSimplePropertyGetter(method) || JavaMethodContractUtil.isPure(method) || isClassAnnotatedImmutable(method)) &&
          isContractAllowedForGetter(method)) {
        return new GetterDescriptor(method);
      }
    }
    return null;
  }

  private static boolean isClassAnnotatedImmutable(PsiMethod method) {
    List<String> annotations = ConcurrencyAnnotationsManager.getInstance(method.getProject()).getImmutableAnnotations();
    return AnnotationUtil.findAnnotation(method.getContainingClass(), annotations) != null;
  }

  private static boolean isContractAllowedForGetter(PsiMethod method) {
    List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(method, null);
    if (contracts.size() == 1) {
      MethodContract contract = contracts.get(0);
      return contract.isTrivial() && contract.getReturnValue().equals(ContractReturnValue.returnNew());
    }
    return contracts.isEmpty();
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ConcurrencyAnnotationsManager;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ArrayElementDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.GetterDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ThisDescriptor;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaTypeValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.util.*;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM;
import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Utility class to help producing values for Java DFA
 */
public final class JavaDfaValueFactory {
  // Methods that while considered as pure may return different results, as depend on the world state
  private static final CallMatcher UNSTABLE_METHODS = CallMatcher.staticCall(
    "java.lang.Thread", "currentThread").parameterCount(0);
  
  private JavaDfaValueFactory() {
  }

  @Contract("_, null -> null")
  public static @Nullable DfaValue getExpressionDfaValue(@NotNull DfaValueFactory factory, @Nullable PsiExpression expression) {
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
        return factory.fromDfType(DfTypes.typedObject(type, DfaPsiUtil.getElementNullability(type, null)));
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

    if (expression instanceof PsiNewExpression) {
      PsiType psiType = expression.getType();
      DfType dfType = psiType == null ? DfType.TOP : TypeConstraints.exact(psiType).asDfType();
      return factory.fromDfType(dfType.meet(DfTypes.NOT_NULL_OBJECT));
    }

    if (expression instanceof PsiLambdaExpression) {
      DfType dfType = JavaDfaHelpers.getFunctionDfType((PsiFunctionalExpression)expression);
      return factory.fromDfType(dfType.meet(DfTypes.NOT_NULL_OBJECT));
    }

    final Object value = JavaConstantExpressionEvaluator.computeConstantExpression(expression, false);
    if (value != null) {
      PsiType type = expression.getType();
      if (type != null) {
        return factory.fromDfType(DfTypes.constant(value, type));
      }
    }

    if (expression instanceof PsiQualifiedExpression) {
      PsiJavaCodeReferenceElement qualifier = ((PsiQualifiedExpression)expression).getQualifier();
      PsiClass target;
      if (qualifier != null) {
        target = tryCast(qualifier.resolve(), PsiClass.class);
      }
      else {
        target = PsiUtil.getContainingClass(expression);
      }
      return target == null
             ? factory.fromDfType(DfTypes.typedObject(expression.getType(), Nullability.NOT_NULL))
             : ThisDescriptor.createThisValue(factory, target);
    }
    return null;
  }

  private static DfaValue createReferenceValue(DfaValueFactory factory, @NotNull PsiReferenceExpression refExpr) {
    PsiElement target = refExpr.resolve();
    if (target instanceof PsiVariable variable) {
      if (!PsiUtil.isAccessedForWriting(refExpr) && !PlainDescriptor.hasInitializationHacks(variable)) {
        DfaValue constValue = getConstantFromVariable(factory, variable);
        if (constValue != null && !maybeUninitializedConstant(constValue, refExpr, variable)) return constValue;
      }
    }
    VariableDescriptor var = getAccessedVariableOrGetter(target);
    if (var == null) {
      return null;
    }

    DfaValue qualifier = getQualifierOrThisValue(factory, refExpr);
    return var.createValue(factory, qualifier);
  }

  /**
   * Returns a DFA variable which represents the qualifier for given reference if possible. For unqualified reference
   * to a non-static member, a variable which represents the corresponding {@code this} may be returned
   *
   * @param factory factory to create new expressions
   * @param refExpr reference to create a qualifier variable for
   * @return a qualifier variable or null if qualifier is unnecessary or cannot be represented as a variable
   */
  public static @Nullable DfaValue getQualifierOrThisValue(DfaValueFactory factory, PsiReferenceExpression refExpr) {
    PsiExpression qualifierExpression = refExpr.getQualifierExpression();
    if (qualifierExpression == null) {
      PsiElement element = refExpr.resolve();
      if (element instanceof PsiMember && !((PsiMember)element).hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass currentClass = PsiUtil.getContainingClass(refExpr);
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
    qualifierExpression = PsiUtil.skipParenthesizedExprDown(qualifierExpression);
    if (qualifierExpression instanceof PsiTypeCastExpression castExpression &&
        castExpression.getType() instanceof PsiClassType) {
      qualifierExpression = castExpression.getOperand();
    }
    return getQualifierValue(factory, qualifierExpression);
  }

  private static @Nullable DfaValue getQualifierValue(DfaValueFactory factory, PsiExpression qualifierExpression) {
    DfaValue qualifierValue = getExpressionDfaValue(factory, qualifierExpression);
    if (qualifierValue == null) return null;
    PsiVariable constVar = qualifierValue.getDfType().getConstantOfType(PsiVariable.class);
    if (constVar != null) {
      return PlainDescriptor.createVariableValue(factory, constVar);
    }
    return qualifierValue;
  }

  static boolean maybeUninitializedConstant(DfaValue constValue,
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
  public static @Nullable VariableDescriptor getAccessedVariableOrGetter(final PsiElement target) {
    return getAccessedVariableOrGetter(target, false);
  }

  /**
   * @param target target element (variable or method)
   * @param stable if true, it's known externally that the access to the element is stable, 
   *               i.e., if the target element is a virtual method, we are definitely accessing the specified one,
   *               and not overridden one.
   * @return the variable descriptor, describing the specified access; null if given element cannot be described as a dataflow variable
   */
  @Contract("null, _ -> null")
  public static @Nullable VariableDescriptor getAccessedVariableOrGetter(@Nullable PsiElement target, boolean stable) {
    SpecialField sf = SpecialField.findSpecialField(target);
    if (sf != null) {
      return sf;
    }
    if (target instanceof PsiVariable variable) {
      return new PlainDescriptor(variable);
    }
    if (target instanceof PsiMethod method) {
      // Assume that methods returning stream always return a new one
      if (InheritanceUtil.isInheritor(method.getReturnType(), JAVA_UTIL_STREAM_BASE_STREAM)) return null;

      PsiField targetField = getFieldForGetter(method, stable);
      if (targetField != null) {
        return new PlainDescriptor(targetField);
      }

      if (!method.isConstructor() && method.getParameterList().isEmpty() &&
          (PropertyUtilBase.isSimplePropertyGetter(method) || JavaMethodContractUtil.isPure(method) || isClassAnnotatedImmutable(method)) &&
          isContractAllowedForGetter(method) &&
          !UNSTABLE_METHODS.methodMatches(method)) {
        return new GetterDescriptor(method);
      }
    }
    return null;
  }
  
  private static @Nullable PsiField getFieldForGetter(@NotNull PsiMethod method, boolean stable) {
    if (!stable && PsiUtil.canBeOverridden(method)) return null;
    if (GetterDescriptor.isKnownStableMethod(method)) return null;
    PsiField field = PropertyUtil.getFieldOfGetter(method);
    if (field == null) return null;
    NullableNotNullManager manager = NullableNotNullManager.getInstance(method.getProject());
    if (manager.isNullable(method, true) && !manager.isNullable(field, true)) {
      // Avoid inlining if getter is marked as nullable, while the field is not.
      // In this rare case, we cannot preserve the nullability warning on the callsite.
      return null;
    }
    return field;
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

  public static @NotNull DfaValue createCommonValue(DfaValueFactory factory, PsiExpression @NotNull [] expressions, PsiType targetType) {
    DfaValue loopElement = null;
    for (PsiExpression expression : expressions) {
      DfaValue expressionValue = getExpressionDfaValue(factory, expression);
      if (expressionValue == null) {
        expressionValue = factory.fromDfType(DfTypes.typedObject(expression.getType(), NullabilityUtil.getExpressionNullability(expression)));
      }
      loopElement = loopElement == null ? expressionValue : loopElement.unite(expressionValue);
      if (DfaTypeValue.isUnknown(loopElement)) break;
    }
    return loopElement == null ? factory.getUnknown() : DfaUtil.boxUnbox(loopElement, targetType);
  }

  /**
   * @param factory factory to create new values
   * @param variable variable to create a constant based on its value
   * @return a value that represents a constant created from variable; null if variable cannot be represented as a constant
   */
  static @Nullable DfaValue getConstantFromVariable(DfaValueFactory factory, PsiVariable variable) {
    if (!variable.hasModifierProperty(PsiModifier.FINAL) || ignoreInitializer(variable)) return null;
    Object value = variable.computeConstantValue();
    PsiType type = variable.getType();
    if (value == null) {
      Boolean boo = computeJavaLangBooleanFieldReference(variable);
      if (boo != null) {
        DfaValue unboxed = factory.fromDfType(DfTypes.booleanValue(boo));
        return factory.getWrapperFactory().createWrapper(DfTypes.typedObject(type, Nullability.NOT_NULL), SpecialField.UNBOX, unboxed);
      }
      if (DfaUtil.isEmptyCollectionConstantField(variable)) {
        return factory.fromDfType(DfTypes.constant(variable, type));
      }
      PsiExpression initializer = PsiFieldImpl.getDetachedInitializer(variable);
      initializer = PsiUtil.skipParenthesizedExprDown(initializer);
      if (initializer instanceof PsiLiteralExpression && initializer.textMatches(JavaKeywords.NULL)) {
        return factory.fromDfType(DfTypes.NULL);
      }
      if (variable instanceof PsiField && variable.hasModifierProperty(PsiModifier.STATIC) && ExpressionUtils.isNewObject(initializer)) {
        return factory.fromDfType(DfTypes.constant(variable, type));
      }
      return null;
    }
    if (value instanceof PsiEnumConstant constant) {
      PsiEnumConstantInitializer cls = constant.getInitializingClass();
      if (cls != null) {
        type = JavaPsiFacade.getElementFactory(variable.getProject()).createType(cls);
      }
    }
    return factory.fromDfType(DfTypes.constant(value, type));
  }

  private static @Nullable Boolean computeJavaLangBooleanFieldReference(final PsiVariable variable) {
    if (!(variable instanceof PsiField)) return null;
    PsiClass psiClass = ((PsiField)variable).getContainingClass();
    if (psiClass == null || !CommonClassNames.JAVA_LANG_BOOLEAN.equals(psiClass.getQualifiedName())) return null;
    @NonNls String name = variable.getName();
    return "TRUE".equals(name) ? Boolean.TRUE : "FALSE".equals(name) ? Boolean.FALSE : null;
  }

  /**
   * @param variable variable to check
   * @return true if variable initializer should be ignored by analysis
   */
  public static boolean ignoreInitializer(PsiVariable variable) {
    if (variable instanceof PsiField && variable.hasModifierProperty(PsiModifier.FINAL)) {
      if (variable.getClass().getName().equals("org.jetbrains.kotlin.light.classes.symbol.fields.SymbolLightFieldForProperty")) {
        // Kotlin light fields may report default value as initializer, which is wrong. See KT-71407
        return true;
      }
      if (variable.getType().equals(PsiTypes.booleanType())) {
        // Skip boolean constant fields as they usually used as control knobs to modify program logic
        // it's better to analyze both true and false values even if it's predefined
        PsiLiteralExpression initializer =
          tryCast(PsiUtil.skipParenthesizedExprDown(variable.getInitializer()), PsiLiteralExpression.class);
        return initializer != null && initializer.getValue() instanceof Boolean;
      }
    }
    return false;
  }
}

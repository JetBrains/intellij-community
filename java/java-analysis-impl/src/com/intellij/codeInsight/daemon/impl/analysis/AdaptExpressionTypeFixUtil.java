// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceExpressionAction;
import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeCastFix;
import com.intellij.codeInsight.daemon.impl.quickfix.WrapExpressionFix;
import com.intellij.codeInsight.daemon.impl.quickfix.WrapWithAdapterMethodCallFix;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.MethodCallUtils;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Utilities to register fixes for mismatching type
 */
class AdaptExpressionTypeFixUtil {
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  private AdaptExpressionTypeFixUtil() { }

  private static void registerPatchParametersFixes(@NotNull HighlightInfo info,
                                                   @NotNull PsiMethodCallExpression call,
                                                   @NotNull PsiMethod method,
                                                   @NotNull PsiType expectedTypeByParent) {
    JavaResolveResult result = call.resolveMethodGenerics();
    if (!(result instanceof MethodCandidateInfo)) return;
    PsiType methodType = method.getReturnType();
    PsiType actualType = ((MethodCandidateInfo)result).getSubstitutor(false).substitute(methodType);
    Map.Entry<PsiTypeParameter, PsiType> substitution = findDesiredSubstitution(expectedTypeByParent, actualType, methodType);
    if (substitution == null) return;
    PsiTypeParameter typeParameter = substitution.getKey();
    PsiType expectedTypeValue = substitution.getValue();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    Set<PsiTypeParameter> set = Set.of(typeParameter);

    if (!PsiTreeUtil.isAncestor(method, typeParameter, true)) {
      registerPatchQualifierFixes(info, call, method, typeParameter, expectedTypeValue, parameters, set);
      return;
    }

    PsiParameter parameter =
      StreamEx.of(parameters).collect(MoreCollectors.onlyOne(p -> PsiTypesUtil.mentionsTypeParameters(p.getType(), set))).orElse(null);
    if (parameter == null) return;
    PsiExpression arg = PsiUtil.skipParenthesizedExprDown(MethodCallUtils.getArgumentForParameter(call, parameter));
    if (arg == null) return;
    PsiType parameterType = parameter.getType();
    if (parameterType instanceof PsiEllipsisType && MethodCallUtils.isVarArgCall(call)) {
      // Replace vararg only if there's single value
      if (call.getArgumentList().getExpressionCount() != parameters.length) return;
      parameterType = ((PsiEllipsisType)parameterType).getComponentType();
    }
    if (parameterType instanceof PsiClassType &&
        ((PsiClassType)parameterType).rawType().equalsToText(CommonClassNames.JAVA_LANG_CLASS) &&
        typeParameter == getSoleTypeParameter(parameterType)) {
      if (expectedTypeValue instanceof PsiClassType && JavaGenericsUtil.isReifiableType(expectedTypeValue)) {
        ReplaceExpressionAction fix = new ReplaceExpressionAction(
          arg, ((PsiClassType)expectedTypeValue).rawType().getCanonicalText() + ".class",
          ((PsiClassType)expectedTypeValue).rawType().getPresentableText() + ".class");
        QuickFixAction.registerQuickFixAction(info, fix);
      }
    }
    PsiType expectedArgType = PsiSubstitutor.EMPTY.put(typeParameter, expectedTypeValue).substitute(parameterType);
    if (arg instanceof PsiLambdaExpression && parameterType instanceof PsiClassType) {
      registerLambdaReturnFixes(info, (PsiLambdaExpression)arg, (PsiClassType)parameterType, expectedArgType, typeParameter);
      return;
    }
    registerExpectedTypeFixes(info, arg, expectedArgType);
  }

  private static void registerPatchQualifierFixes(@NotNull HighlightInfo info,
                                                  @NotNull PsiMethodCallExpression call,
                                                  @NotNull PsiMethod method,
                                                  @NotNull PsiTypeParameter typeParameter,
                                                  @NotNull PsiType expectedTypeValue,
                                                  @NotNull PsiParameter @NotNull [] parameters,
                                                  @NotNull Set<PsiTypeParameter> set) {
    PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
    if (qualifierCall == null) return;
    JavaResolveResult qualifierResolve = qualifierCall.resolveMethodGenerics();
    PsiMethod qualifierMethod = tryCast(qualifierResolve.getElement(), PsiMethod.class);
    if (qualifierMethod == null) return;
    PsiClassType qualifierType = tryCast(qualifierMethod.getReturnType(), PsiClassType.class);
    if (qualifierType == null) return;
    if (ContainerUtil.exists(parameters, p -> PsiTypesUtil.mentionsTypeParameters(p.getType(), set))) return;
    PsiClassType.ClassResolveResult classResolveResult = qualifierType.resolveGenerics();
    PsiClass qualifierClass = classResolveResult.getElement();
    if (qualifierClass == null) return;
    PsiType expectedQualifierType = JavaPsiFacade.getElementFactory(method.getProject())
      .createType(qualifierClass, classResolveResult.getSubstitutor().put(typeParameter, expectedTypeValue));
    if (!expectedQualifierType.equals(qualifierCall.getType())) {
      registerPatchParametersFixes(info, qualifierCall, qualifierMethod, expectedQualifierType);
    }
  }

  private static void registerLambdaReturnFixes(@NotNull HighlightInfo info,
                                                @NotNull PsiLambdaExpression arg,
                                                @NotNull PsiClassType parameterType,
                                                @Nullable PsiType expectedArgType,
                                                @NotNull PsiTypeParameter typeParameter) {
    if (!(expectedArgType instanceof PsiClassType)) return;
    PsiExpression lambdaBody = LambdaUtil.extractSingleExpressionFromBody(arg.getBody());
    if (lambdaBody == null) return;
    PsiClass fnInterface = parameterType.resolve();
    if (fnInterface == null) return;
    PsiType[] fnArgs = parameterType.getParameters();
    PsiTypeParameter[] fnParams = fnInterface.getTypeParameters();
    if (fnArgs.length != fnParams.length) return;
    PsiType fnTypeArgumentToChange = StreamEx.of(fnArgs)
      .collect(MoreCollectors.onlyOne(c -> PsiTypesUtil.mentionsTypeParameters(c, Set.of(typeParameter)))).orElse(null);
    if (fnTypeArgumentToChange == null) return;
    int index = ArrayUtil.indexOf(fnArgs, fnTypeArgumentToChange);
    if (index == -1) return;
    PsiTypeParameter fnParam = fnParams[index];
    PsiMethod sam = LambdaUtil.getFunctionalInterfaceMethod(fnInterface);
    if (sam == null) return;
    Set<PsiTypeParameter> fnParamSet = Set.of(fnParam);
    PsiType returnType = sam.getReturnType();
    if (!PsiTypesUtil.mentionsTypeParameters(returnType, fnParamSet) ||
        ContainerUtil.exists(sam.getParameterList().getParameters(), p -> PsiTypesUtil.mentionsTypeParameters(p.getType(), fnParamSet))) {
      return;
    }
    PsiType expectedFnReturnType = LambdaUtil.getFunctionalInterfaceReturnType(expectedArgType);
    if (expectedFnReturnType == null) return;
    registerExpectedTypeFixes(info, lambdaBody, expectedFnReturnType);
  }

  /**
   * Registers fixes (if any) that update code to match the expression type with the desired type.
   *
   * @param info error highlighting to attach fixes to
   * @param expression expression whose type is incorrect
   * @param expectedType desired expression type.
   */
  static void registerExpectedTypeFixes(@NotNull HighlightInfo info, @NotNull PsiExpression expression, @Nullable PsiType expectedType) {
    if (expectedType == null) return;
    expectedType = GenericsUtil.getVariableTypeByExpressionType(expectedType);
    TextRange range = expression.getTextRange();
    String role = info.startOffset == range.getStartOffset() && info.endOffset == range.getEndOffset() ? null : getRole(expression);
    QuickFixAction.registerQuickFixAction(info, new WrapWithAdapterMethodCallFix(expectedType, expression, role));
    QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createWrapWithOptionalFix(expectedType, expression));
    QuickFixAction.registerQuickFixAction(info, new WrapExpressionFix(expectedType, expression, role));
    PsiType actualType = expression.getType();
    if (expression instanceof PsiMethodCallExpression) {
      JavaResolveResult result = ((PsiMethodCallExpression)expression).resolveMethodGenerics();
      if (result instanceof MethodCandidateInfo) {
        actualType = ((MethodCandidateInfo)result).getSubstitutor(false).substitute(actualType);
      }
    }
    if (expectedType instanceof PsiArrayType) {
      PsiType erasedValueType = TypeConversionUtil.erasure(actualType);
      if (erasedValueType != null &&
          TypeConversionUtil.isAssignable(((PsiArrayType)expectedType).getComponentType(), erasedValueType)) {
        QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createSurroundWithArrayFix(null, expression));
      }
    }
    HighlightFixUtil.registerCollectionToArrayFixAction(info, actualType, expectedType, expression);
    PsiType castToType = suggestCastTo(expectedType, actualType);
    if (castToType != null) {
      QuickFixAction.registerQuickFixAction(info, new AddTypeCastFix(castToType, expression, role));
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethod argMethod = ((PsiMethodCallExpression)expression).resolveMethod();
      if (argMethod != null) {
        registerPatchParametersFixes(info, (PsiMethodCallExpression)expression, argMethod, expectedType);
      }
    }
  }

  private static String getRole(@NotNull PsiExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiExpressionList) {
      int count = ((PsiExpressionList)parent).getExpressionCount();
      if (count > 1) {
        long index = StreamEx.of(((PsiExpressionList)parent).getExpressions())
          .map(PsiUtil::skipParenthesizedExprDown).indexOf(expression).orElse(-1);
        if (index != -1) {
          return QuickFixBundle.message("fix.expression.role.nth.argument", index + 1);
        }
      }
      return QuickFixBundle.message("fix.expression.role.argument");
    }
    if (parent instanceof PsiLambdaExpression) {
      return QuickFixBundle.message("fix.expression.role.lambda.return");
    }
    return null;
  }

  private static @Nullable Map.Entry<PsiTypeParameter, PsiType> findDesiredSubstitution(@Nullable PsiType expected,
                                                                                        @Nullable PsiType actual,
                                                                                        @Nullable PsiType methodType) {
    if (expected == null || actual == null || methodType == null) return null;
    if (expected instanceof PsiArrayType && actual instanceof PsiArrayType && methodType instanceof PsiArrayType) {
      return findDesiredSubstitution(((PsiArrayType)expected).getComponentType(),
                                     ((PsiArrayType)actual).getComponentType(),
                                     ((PsiArrayType)methodType).getComponentType());
    }
    if (expected instanceof PsiWildcardType && ((PsiWildcardType)expected).isExtends()) {
      expected = ((PsiWildcardType)expected).getExtendsBound();
    }
    if (!(methodType instanceof PsiClassType)) return null;
    PsiClass methodClass = ((PsiClassType)methodType).resolve();
    if (methodClass == null) return null;
    if (methodClass instanceof PsiTypeParameter) {
      if (!expected.equals(actual) && !(expected instanceof PsiPrimitiveType)) {
        for (PsiClassType superType : methodClass.getSuperTypes()) {
          PsiSubstitutor substitutor = PsiSubstitutor.EMPTY.put((PsiTypeParameter)methodClass, expected);
          if (!substitutor.substitute(superType).isAssignableFrom(expected)) return null;
        }
        return Map.entry((PsiTypeParameter)methodClass, expected);
      }
      return null;
    }
    if (!(expected instanceof PsiClassType) || !(actual instanceof PsiClassType)) return null;
    PsiClass expectedClass = ((PsiClassType)expected).resolve();
    PsiClass actualClass = ((PsiClassType)actual).resolve();
    if (expectedClass == null || actualClass == null || !actualClass.isEquivalentTo(methodClass)) return null;
    if (!expectedClass.isEquivalentTo(actualClass)) {
      methodType = trySubstitute(methodType, expectedClass);
      actual = trySubstitute(actual, expectedClass);
      if (methodType == null || actual == null) return null;
    }
    PsiType[] methodTypeParameters = ((PsiClassType)methodType).getParameters();
    PsiType[] expectedTypeParameters = ((PsiClassType)expected).getParameters();
    PsiType[] actualTypeParameters = ((PsiClassType)actual).getParameters();
    if (methodTypeParameters.length != expectedTypeParameters.length || methodTypeParameters.length != actualTypeParameters.length) {
      return null;
    }
    Map.Entry<PsiTypeParameter, PsiType> existing = null;
    for (int i = 0; i < methodTypeParameters.length; i++) {
      Map.Entry<PsiTypeParameter, PsiType> substitution =
        findDesiredSubstitution(expectedTypeParameters[i], actualTypeParameters[i], methodTypeParameters[i]);
      if (existing == null) {
        existing = substitution;
      }
      else if (!existing.equals(substitution)) {
        return null;
      }
    }
    return existing;
  }

  private static @Nullable PsiType trySubstitute(@NotNull PsiType type, @NotNull PsiClass superClass) {
    if (!(type instanceof PsiClassType)) return null;
    PsiClassType.ClassResolveResult result = ((PsiClassType)type).resolveGenerics();
    PsiClass psiClass = result.getElement();
    if (psiClass == null) return null;
    if (!psiClass.isInheritor(superClass, true)) return null;
    PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, psiClass, result.getSubstitutor());
    return JavaPsiFacade.getElementFactory(superClass.getProject()).createType(superClass, substitutor);
  }

  private static @Nullable PsiTypeParameter getSoleTypeParameter(@Nullable PsiType type) {
    if (!(type instanceof PsiClassType)) return null;
    PsiType[] parameters = ((PsiClassType)type).getParameters();
    if (parameters.length != 1) return null;
    PsiType parameter = parameters[0];
    if (parameter instanceof PsiWildcardType) {
      parameter = ((PsiWildcardType)parameter).getExtendsBound();
    }
    if (!(parameter instanceof PsiClassType)) return null;
    return tryCast(((PsiClassType)parameter).resolve(), PsiTypeParameter.class);
  }

  static @Nullable PsiType suggestCastTo(@Nullable PsiType expectedTypeByParent, @Nullable PsiType actualType) {
    if (expectedTypeByParent == null || actualType == null) return null;
    if (TypeConversionUtil.isAssignable(expectedTypeByParent, actualType)) return null;
    if (TypeConversionUtil.areTypesConvertible(actualType, expectedTypeByParent)) return expectedTypeByParent;
    if (actualType instanceof PsiPrimitiveType) {
      PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(expectedTypeByParent);
      if (unboxedType != null && TypeConversionUtil.areTypesConvertible(actualType, unboxedType)) {
        return unboxedType;
      }
    }
    return null;
  }
}

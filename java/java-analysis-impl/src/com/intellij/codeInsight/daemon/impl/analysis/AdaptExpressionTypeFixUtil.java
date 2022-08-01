// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceExpressionAction;
import com.intellij.codeInsight.daemon.impl.quickfix.WrapExpressionFix;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
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
  private AdaptExpressionTypeFixUtil() {}

  static void registerPatchParametersFixes(@NotNull HighlightInfo info,
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
    if (!PsiTreeUtil.isAncestor(method, typeParameter, true)) return;
    PsiType expectedTypeValue = substitution.getValue();

    PsiParameter[] parameters = method.getParameterList().getParameters();
    Set<PsiTypeParameter> set = Set.of(typeParameter);
    PsiParameter parameter =
      StreamEx.of(parameters).collect(MoreCollectors.onlyOne(p -> PsiTypesUtil.mentionsTypeParameters(p.getType(), set))).orElse(null);
    if (parameter == null) return;
    PsiExpression arg = PsiUtil.skipParenthesizedExprDown(MethodCallUtils.getArgumentForParameter(call, parameter));
    if (arg == null) return;
    PsiClassType parameterType = tryCast(parameter.getType(), PsiClassType.class);
    if (parameterType == null) return;
    if (parameterType.rawType().equalsToText(CommonClassNames.JAVA_LANG_CLASS) && typeParameter == getSoleTypeParameter(parameterType)) {
      if (expectedTypeValue instanceof PsiClassType && JavaGenericsUtil.isReifiableType(expectedTypeValue)) {
        ReplaceExpressionAction fix = new ReplaceExpressionAction(
          arg, ((PsiClassType)expectedTypeValue).rawType().getCanonicalText() + ".class",
          ((PsiClassType)expectedTypeValue).rawType().getPresentableText() + ".class");
        QuickFixAction.registerQuickFixAction(info, fix);
      }
    }
    PsiType expectedArgType = PsiSubstitutor.EMPTY.put(typeParameter, expectedTypeValue).substitute(parameterType);
    if (arg instanceof PsiLambdaExpression) {
      registerLambdaReturnFixes(info, (PsiLambdaExpression)arg, parameterType, expectedArgType, typeParameter);
      return;
    }
    registerExpectedTypeFixes(info, arg, expectedArgType);
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

  static void registerExpectedTypeFixes(@NotNull HighlightInfo info, @NotNull PsiExpression arg, @Nullable PsiType expectedType) {
    if (expectedType == null) return;
    expectedType = GenericsUtil.getVariableTypeByExpressionType(expectedType);
    QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createWrapWithAdapterFix(expectedType, arg));
    QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createWrapWithOptionalFix(expectedType, arg));
    QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createWrapExpressionFix(expectedType, arg));
    PsiType argType = arg.getType();
    if (arg instanceof PsiMethodCallExpression) {
      JavaResolveResult result = ((PsiMethodCallExpression)arg).resolveMethodGenerics();
      if (result instanceof MethodCandidateInfo) {
        argType = ((MethodCandidateInfo)result).getSubstitutor(false).substitute(argType);
      }
    }
    PsiType castToType = suggestCastTo(expectedType, argType);
    if (castToType != null) {
      QuickFixAction.registerQuickFixAction(info, QUICK_FIX_FACTORY.createAddTypeCastFix(castToType, arg));
    }
    if (arg instanceof PsiMethodCallExpression) {
      PsiMethod argMethod = ((PsiMethodCallExpression)arg).resolveMethod();
      if (argMethod != null) {
        registerPatchParametersFixes(info, (PsiMethodCallExpression)arg, argMethod, expectedType);
      }
    }
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
    if (!(methodType instanceof PsiClassType) || !(expected instanceof PsiClassType) || !(actual instanceof PsiClassType)) return null;
    PsiClass methodClass = ((PsiClassType)methodType).resolve();
    PsiClass expectedClass = ((PsiClassType)expected).resolve();
    PsiClass actualClass = ((PsiClassType)actual).resolve();
    if (methodClass == null || expectedClass == null || actualClass == null) return null;
    if (methodClass instanceof PsiTypeParameter) {
      if (!expected.equals(actual)) {
        return Map.entry((PsiTypeParameter)methodClass, expected);
      }
      return null;
    }
    if (!expectedClass.isEquivalentTo(actualClass) || !expectedClass.isEquivalentTo(methodClass)) return null;
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

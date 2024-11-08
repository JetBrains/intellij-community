// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.MethodCallUtils;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Utilities to register fixes for mismatching type
 */
final class AdaptExpressionTypeFixUtil {

  private AdaptExpressionTypeFixUtil() { }

  private static void registerPatchParametersFixes(@NotNull HighlightInfo.Builder info,
                                                   @NotNull TextRange textRange,
                                                   @NotNull PsiMethodCallExpression call,
                                                   @NotNull PsiMethod method,
                                                   @NotNull PsiType expectedTypeByParent,
                                                   @NotNull PsiType actualType) {
    JavaResolveResult result = call.resolveMethodGenerics();
    if (!(result instanceof MethodCandidateInfo candidateInfo)) return;
    PsiType methodType = method.getReturnType();
    Substitution.TypeParameterSubstitution substitution =
      tryCast(findDesiredSubstitution(expectedTypeByParent, actualType, methodType, 0), Substitution.TypeParameterSubstitution.class);
    if (substitution == null) return;
    PsiTypeParameter typeParameter = substitution.myTypeParameter;
    PsiType expectedTypeValue = substitution.myDesiredType;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    Set<PsiTypeParameter> set = Set.of(typeParameter);

    if (!PsiTreeUtil.isAncestor(method, typeParameter, true)) {
      registerPatchQualifierFixes(info, textRange, call, method, typeParameter, expectedTypeValue, parameters, set);
      return;
    }

    PsiParameter parameter =
      StreamEx.of(parameters).collect(MoreCollectors.onlyOne(p -> PsiTypesUtil.mentionsTypeParameters(p.getType(), set))).orElse(null);
    PsiSubstitutor substitutor = candidateInfo.getSubstitutor(false);
    PsiSubstitutor desiredSubstitutor = substitutor.put(typeParameter, expectedTypeValue);
    if (parameter == null) {
      parameter = findWrongParameter(call, method, desiredSubstitutor, typeParameter);
    }
    if (parameter == null) return;
    PsiExpression arg = PsiUtil.skipParenthesizedExprDown(MethodCallUtils.getArgumentForParameter(call, parameter));
    if (arg == null) return;
    PsiType parameterType = parameter.getType();
    if (parameterType instanceof PsiEllipsisType ellipsisType && MethodCallUtils.isVarArgCall(call)) {
      // Replace vararg only if there's single value
      if (call.getArgumentList().getExpressionCount() != parameters.length) return;
      parameterType = ellipsisType.getComponentType();
    }
    if (parameterType instanceof PsiClassType psiClassType &&
        psiClassType.rawType().equalsToText(CommonClassNames.JAVA_LANG_CLASS) &&
        typeParameter == getSoleTypeParameter(parameterType)) {
      if (expectedTypeValue instanceof PsiClassType classType && JavaGenericsUtil.isReifiableType(expectedTypeValue)) {
        ReplaceExpressionAction fix = new ReplaceExpressionAction(
          arg, classType.rawType().getCanonicalText() + ".class",
          classType.rawType().getPresentableText() + ".class");
        info.registerFix(fix, null, null, null, null);
      }
    }
    PsiType expectedArgType = desiredSubstitutor.substitute(parameterType);
    if (arg instanceof PsiLambdaExpression && parameterType instanceof PsiClassType) {
      registerLambdaReturnFixes(info, textRange, (PsiLambdaExpression)arg, (PsiClassType)parameterType, expectedArgType, typeParameter);
      return;
    }
    PsiType actualArgType = PsiPolyExpressionUtil.isPolyExpression(arg) ?
                            substitutor.put(typeParameter, substitution.myActualType).substitute(parameterType) :
                            arg.getType();
    registerExpectedTypeFixes(info, textRange, arg, expectedArgType, actualArgType);
  }

  private static @Nullable PsiParameter findWrongParameter(@NotNull PsiMethodCallExpression call,
                                                           @NotNull PsiMethod method,
                                                           @NotNull PsiSubstitutor substitutor, @NotNull PsiTypeParameter typeParameter) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    PsiExpression[] expressions = call.getArgumentList().getExpressions();
    if (expressions.length != parameters.length) return null;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(call.getProject());
    List<PsiParameter> candidates = new ArrayList<>();
    for (int i = 0; i < parameters.length; i++) {
      PsiType type = parameters[i].getType();
      if (!PsiTypesUtil.mentionsTypeParameters(type, Set.of(typeParameter))) continue;
      PsiType substituted = substitutor.substitute(type);
      PsiTypeCastExpression cast = (PsiTypeCastExpression)factory.createExpressionFromText("(x)null", call);
      PsiTypeElement typeElement = factory.createTypeElement(substituted);
      try {
        Objects.requireNonNull(cast.getCastType()).replace(typeElement);
      }
      catch (IncorrectOperationException ignored) {
        // Malformed type
        continue;
      }
      PsiMethodCallExpression copy = (PsiMethodCallExpression)LambdaUtil.copyTopLevelCall(call);
      copy.getArgumentList().getExpressions()[i].replace(cast);
      JavaResolveResult resolveResult = copy.resolveMethodGenerics();
      if (resolveResult instanceof MethodCandidateInfo info &&
          info.getElement() == method &&
          info.getInferenceErrorMessage() == null) {
        candidates.add(parameters[i]);
      }
    }
    return ContainerUtil.getOnlyItem(candidates);
  }

  private static void registerPatchQualifierFixes(@NotNull HighlightInfo.Builder info,
                                                  @NotNull TextRange textRange,
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
    PsiType actualType = qualifierCall.getType();
    if (actualType != null && !expectedQualifierType.equals(actualType)) {
      registerPatchParametersFixes(info, textRange, qualifierCall, qualifierMethod, expectedQualifierType, actualType);
    }
  }

  private static void registerLambdaReturnFixes(@NotNull HighlightInfo.Builder info,
                                                @NotNull TextRange textRange,
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
    registerExpectedTypeFixes(info, textRange, lambdaBody, expectedFnReturnType);
  }

  /**
   * Registers fixes (if any) that update code to match the expression type with the desired type.
   *
   * @param info         error highlighting to attach fixes to
   * @param expression   expression whose type is incorrect
   * @param expectedType desired expression type.
   */
  static void registerExpectedTypeFixes(@NotNull HighlightInfo.Builder info, @NotNull TextRange textRange,@NotNull PsiExpression expression, @Nullable PsiType expectedType) {
    PsiType actualType;
    if (PsiPolyExpressionUtil.isPolyExpression(expression)) {
      actualType = ((PsiExpression)expression.copy()).getType();
    }
    else {
      actualType = expression.getType();
    }
    registerExpectedTypeFixes(info, textRange, expression, expectedType, actualType);
  }

  /**
   * Registers fixes (if any) that update code to match the expression type with the desired type.
   *
   * @param info         error highlighting to attach fixes to
   * @param expression   expression whose type is incorrect
   * @param expectedType desired expression type
   * @param actualType   actual expression type
   */
  static void registerExpectedTypeFixes(@NotNull HighlightInfo.Builder info,
                                        @NotNull TextRange textRange,
                                        @NotNull PsiExpression expression,
                                        @Nullable PsiType expectedType,
                                        @Nullable PsiType actualType) {
    if (actualType == null || expectedType == null) return;
    boolean mentionsTypeArgument = mentionsTypeArgument(expression, actualType);
    expectedType = GenericsUtil.getVariableTypeByExpressionType(expectedType);
    TextRange range = expression.getTextRange();
    String role = textRange.equals(range) ? null : getRole(expression);
    if (!mentionsTypeArgument) {
      IntentionAction action3 = new WrapWithAdapterMethodCallFix(expectedType, expression, role);
      info.registerFix(action3, null, null, null, null);
      IntentionAction action2 = QuickFixFactory.getInstance().createWrapWithOptionalFix(expectedType, expression);
      info.registerFix(action2, null, null, null, null);
      var action1 = new WrapExpressionFix(expectedType, expression, role);
      info.registerFix(action1, null, null, null, null);
      PsiType castToType = suggestCastTo(expression, expectedType, actualType);
      if (castToType != null) {
        ModCommandAction action = new AddTypeCastFix(castToType, expression, role);
        info.registerFix(action, null, null, null, null);
      }
    }
    if (expectedType instanceof PsiArrayType arrayType) {
      PsiType erasedValueType = TypeConversionUtil.erasure(actualType);
      if (erasedValueType != null &&
          TypeConversionUtil.isAssignable(arrayType.getComponentType(), erasedValueType)) {
        IntentionAction action = QuickFixFactory.getInstance().createSurroundWithArrayFix(null, expression);
        info.registerFix(action, null, null, null, null);
      }
    }
    HighlightFixUtil.registerCollectionToArrayFixAction(info, actualType, expectedType, expression);
    if (expression instanceof PsiMethodCallExpression call) {
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier != null) {
        PsiType type = qualifier.getType();
        if (type != null && expectedType.isAssignableFrom(type)) {
          info.registerFix(new ReplaceWithQualifierFix(call, role), null, null, null, null);
        }
      }
      PsiMethod argMethod = call.resolveMethod();
      if (argMethod != null) {
        registerPatchParametersFixes(info, textRange, call, argMethod, expectedType, actualType);
      }
    }
  }

  private static boolean mentionsTypeArgument(PsiExpression expression, PsiType type) {
    if (!(expression instanceof PsiMethodCallExpression call)) return false;
    PsiMethod method = call.resolveMethod();
    if (method == null) return false;
    return PsiTypesUtil.mentionsTypeParameters(type, Set.of(method.getTypeParameters()));
  }

  @Nls
  private static String getRole(@NotNull PsiExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiExpressionList list) {
      int count = list.getExpressionCount();
      if (count > 1) {
        long index = StreamEx.of(list.getExpressions())
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

  private interface Substitution {
    Substitution TOP = new Substitution() {
      @Override
      public String toString() {
        return "TOP";
      }
    };
    Substitution BOTTOM = new Substitution() {
      @Override
      public String toString() {
        return "BOTTOM";
      }
    };

    class TypeParameterSubstitution implements Substitution {
      final @NotNull PsiTypeParameter myTypeParameter;
      final @NotNull PsiType myActualType;
      final @NotNull PsiType myDesiredType;

      public TypeParameterSubstitution(@NotNull PsiTypeParameter parameter, @NotNull PsiType actualType, @NotNull PsiType desiredType) {
        myTypeParameter = parameter;
        myActualType = actualType;
        myDesiredType = desiredType;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeParameterSubstitution that = (TypeParameterSubstitution)o;
        return myTypeParameter.equals(that.myTypeParameter) && myDesiredType.equals(that.myDesiredType);
      }

      @Override
      public int hashCode() {
        return Objects.hash(myTypeParameter, myDesiredType);
      }

      @Override
      public String toString() {
        return myTypeParameter.getName() + "->" + myDesiredType.getCanonicalText();
      }
    }
  }


  private static @NotNull Substitution findDesiredSubstitution(@Nullable PsiType expected,
                                                               @Nullable PsiType actual,
                                                               @Nullable PsiType methodType,
                                                               int level) {
    if (expected == null || actual == null || methodType == null) return Substitution.BOTTOM;
    if (expected instanceof PsiArrayType expectedArrayType && actual instanceof PsiArrayType actualArrayType &&
        methodType instanceof PsiArrayType methodArrayType) {
      return findDesiredSubstitution(expectedArrayType.getComponentType(),
                                     actualArrayType.getComponentType(),
                                     methodArrayType.getComponentType(),
                                     level);
    }
    if (expected.equals(actual)) return Substitution.TOP;
    if (!(methodType instanceof PsiClassType classType)) return Substitution.BOTTOM;
    PsiClass methodClass = classType.resolve();
    if (methodClass == null) return Substitution.BOTTOM;
    if (methodClass instanceof PsiTypeParameter parameter) {
      if (!(expected instanceof PsiPrimitiveType)) {
        for (PsiClassType superType : methodClass.getSuperTypes()) {
          PsiSubstitutor substitutor = PsiSubstitutor.EMPTY.put(parameter, expected);
          if (!substitutor.substitute(superType).isAssignableFrom(expected)) return Substitution.BOTTOM;
        }
        return new Substitution.TypeParameterSubstitution(parameter, actual, expected);
      }
      return Substitution.BOTTOM;
    }
    if (!(expected instanceof PsiClassType expectedClassType) || !(actual instanceof PsiClassType actualClassType)) return Substitution.BOTTOM;
    PsiClass expectedClass = expectedClassType.resolve();
    PsiClass actualClass = actualClassType.resolve();
    if (expectedClass == null || actualClass == null || !actualClass.isEquivalentTo(methodClass)) return Substitution.BOTTOM;
    if (!expectedClass.isEquivalentTo(actualClass)) {
      if (level > 0) return Substitution.BOTTOM;
      methodType = trySubstitute(methodType, expectedClass);
      actual = trySubstitute(actual, expectedClass);
      if (methodType == null || actual == null) return Substitution.BOTTOM;
    }
    PsiType[] methodTypeParameters = ((PsiClassType)methodType).getParameters();
    PsiType[] expectedTypeParameters = expectedClassType.getParameters();
    PsiType[] actualTypeParameters = ((PsiClassType)actual).getParameters();
    if (methodTypeParameters.length != expectedTypeParameters.length || methodTypeParameters.length != actualTypeParameters.length) {
      return Substitution.BOTTOM;
    }
    Substitution existing = Substitution.TOP;
    for (int i = 0; i < methodTypeParameters.length; i++) {
      Substitution substitution = findDesiredSubstitution(expectedTypeParameters[i], actualTypeParameters[i], methodTypeParameters[i], level+1);
      if (existing == Substitution.TOP) {
        existing = substitution;
      }
      else if (substitution != Substitution.TOP && !existing.equals(substitution)) {
        return Substitution.BOTTOM;
      }
    }
    return existing;
  }

  private static @Nullable PsiType trySubstitute(@NotNull PsiType type, @NotNull PsiClass superClass) {
    if (!(type instanceof PsiClassType classType)) return null;
    PsiClassType.ClassResolveResult result = classType.resolveGenerics();
    PsiClass psiClass = result.getElement();
    if (psiClass == null) return null;
    if (!psiClass.isInheritor(superClass, true)) return null;
    PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, psiClass, result.getSubstitutor());
    return JavaPsiFacade.getElementFactory(superClass.getProject()).createType(superClass, substitutor);
  }

  private static @Nullable PsiTypeParameter getSoleTypeParameter(@Nullable PsiType type) {
    if (!(type instanceof PsiClassType classType)) return null;
    PsiType[] parameters = classType.getParameters();
    if (parameters.length != 1) return null;
    PsiType parameter = parameters[0];
    if (parameter instanceof PsiWildcardType wildcardType) {
      parameter = wildcardType.getExtendsBound();
    }
    if (!(parameter instanceof PsiClassType paramClassType)) return null;
    return tryCast(paramClassType.resolve(), PsiTypeParameter.class);
  }

  static @Nullable PsiType suggestCastTo(@NotNull PsiExpression expression,
                                         @Nullable PsiType expectedTypeByParent, @Nullable PsiType actualType) {
    if (expectedTypeByParent == null || actualType == null) return null;
    if (TypeConversionUtil.isAssignable(expectedTypeByParent, actualType)) return null;
    boolean convertible = expression instanceof PsiNewExpression ? expectedTypeByParent.isAssignableFrom(actualType) : 
                          TypeConversionUtil.areTypesConvertible(actualType, expectedTypeByParent);
    if (convertible) return expectedTypeByParent;
    if (actualType instanceof PsiPrimitiveType) {
      PsiPrimitiveType unboxedType = PsiPrimitiveType.getUnboxedType(expectedTypeByParent);
      if (unboxedType != null && TypeConversionUtil.areTypesConvertible(actualType, unboxedType)) {
        return unboxedType;
      }
    }
    return null;
  }
}

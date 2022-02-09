// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LambdaHighlightingUtil {
  private static final Logger LOG = Logger.getInstance(LambdaHighlightingUtil.class);

  public static @NlsContexts.DetailedDescription String checkInterfaceFunctional(@NotNull PsiClass psiClass) {
    return checkInterfaceFunctional(psiClass, JavaErrorBundle.message("target.type.of.a.lambda.conversion.must.be.an.interface"));
  }

  static @NlsContexts.DetailedDescription String checkInterfaceFunctional(@NotNull PsiClass psiClass, @NotNull @Nls String interfaceNonFunctionalMessage) {
    if (psiClass instanceof PsiTypeParameter) return null; //should be logged as cyclic inference
    List<HierarchicalMethodSignature> signatures = LambdaUtil.findFunctionCandidates(psiClass);
    if (signatures == null) return interfaceNonFunctionalMessage;
    if (signatures.isEmpty()) return JavaErrorBundle.message("no.target.method.found");
    if (signatures.size() == 1) {
      return null;
    }
    return JavaErrorBundle.message("multiple.non.overriding.abstract.methods.found.in.interface.0", HighlightUtil.formatClass(psiClass));
  }

  static HighlightInfo checkParametersCompatible(@NotNull PsiLambdaExpression expression,
                                                 PsiParameter @NotNull [] methodParameters,
                                                 @NotNull PsiSubstitutor substitutor) {
    PsiParameter[] lambdaParameters = expression.getParameterList().getParameters();
    if (lambdaParameters.length != methodParameters.length) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(expression.getParameterList())
        .descriptionAndTooltip(JavaErrorBundle.message("incompatible.parameter.types.in.lambda.wrong.number.of.parameters", methodParameters.length, lambdaParameters.length))
        .create();
    }
    boolean hasFormalParameterTypes = expression.hasFormalParameterTypes();
    for (int i = 0; i < lambdaParameters.length; i++) {
      PsiParameter lambdaParameter = lambdaParameters[i];
      PsiType lambdaParameterType = lambdaParameter.getType();
      PsiType substitutedParamType = substitutor.substitute(methodParameters[i].getType());
      if (hasFormalParameterTypes &&!PsiTypesUtil.compareTypes(lambdaParameterType, substitutedParamType, true) ||
          !TypeConversionUtil.isAssignable(substitutedParamType, lambdaParameterType)) {
        String expectedType = substitutedParamType != null ? substitutedParamType.getPresentableText() : null;
        String actualType = lambdaParameterType.getPresentableText();
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(expression.getParameterList())
          .descriptionAndTooltip(
            JavaErrorBundle.message("incompatible.parameter.types.in.lambda", expectedType, actualType))
          .create();
      }
    }
    return null;
  }

  public static boolean insertSemicolonAfter(@NotNull PsiLambdaExpression lambdaExpression) {
    return lambdaExpression.getBody() instanceof PsiCodeBlock || insertSemicolon(lambdaExpression.getParent());
  }

  public static boolean insertSemicolon(PsiElement parent) {
    return !(parent instanceof PsiExpressionList) && !(parent instanceof PsiExpression);
  }

  public static @NlsContexts.DetailedDescription String checkInterfaceFunctional(@NotNull PsiType functionalInterfaceType) {
    if (functionalInterfaceType instanceof PsiIntersectionType) {
      Set<MethodSignature> signatures = new HashSet<>();
      for (PsiType type : ((PsiIntersectionType)functionalInterfaceType).getConjuncts()) {
        if (checkInterfaceFunctional(type) == null) {
          MethodSignature signature = LambdaUtil.getFunction(PsiUtil.resolveClassInType(type));
          LOG.assertTrue(signature != null, type.getCanonicalText());
          signatures.add(signature);
        }
      }

      if (signatures.size() > 1) {
        return JavaErrorBundle.message("multiple.non.overriding.abstract.methods.found.in.0", functionalInterfaceType.getPresentableText());
      }
      return null;
    }
    PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    PsiClass aClass = resolveResult.getElement();
    if (aClass != null) {
      if (aClass instanceof PsiTypeParameter) return null; //should be logged as cyclic inference
      MethodSignature functionalMethod = LambdaUtil.getFunction(aClass);
      if (functionalMethod != null && functionalMethod.getTypeParameters().length > 0) return JavaErrorBundle
        .message("target.method.is.generic");
      return checkInterfaceFunctional(aClass);
    }
    return JavaErrorBundle.message("not.a.functional.interface",functionalInterfaceType.getPresentableText());
  }

  static HighlightInfo checkConsistentParameterDeclaration(@NotNull PsiLambdaExpression expression) {
    PsiParameter[] parameters = expression.getParameterList().getParameters();
    if (parameters.length < 2) return null;
    boolean hasExplicitParameterTypes = hasExplicitType(parameters[0]);
    for (int i = 1; i < parameters.length; i++) {
      if (hasExplicitParameterTypes != hasExplicitType(parameters[i])) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .descriptionAndTooltip(JavaErrorBundle.message("lambda.parameters.consistency.message"))
                            .range(expression.getParameterList())
                            .create();
      }
    }

    return null;
  }

  private static boolean hasExplicitType(@NotNull PsiParameter parameter) {
    PsiTypeElement typeElement = parameter.getTypeElement();
    return typeElement != null && !typeElement.isInferredType();
  }
}

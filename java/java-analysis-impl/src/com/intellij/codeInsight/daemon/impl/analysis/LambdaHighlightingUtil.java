// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.JavaModuleSystemEx.ErrorWithFixes;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.psi.LambdaUtil.getFunction;
import static com.intellij.psi.LambdaUtil.getTargetMethod;

public final class LambdaHighlightingUtil {
  private static final Logger LOG = Logger.getInstance(LambdaHighlightingUtil.class);

  static @NlsContexts.DetailedDescription String checkInterfaceFunctional(@NotNull PsiClass psiClass) {
    return checkInterfaceFunctional(psiClass, JavaErrorBundle.message("target.type.of.a.lambda.conversion.must.be.an.interface"));
  }

  static @NlsContexts.DetailedDescription String checkInterfaceFunctional(@NotNull PsiClass psiClass, @NotNull @Nls String interfaceNonFunctionalMessage) {
    if (psiClass instanceof PsiTypeParameter) return null; //should be logged as cyclic inference
    return switch (LambdaUtil.checkInterfaceFunctional(psiClass)) {
      case VALID -> null;
      case NOT_INTERFACE -> interfaceNonFunctionalMessage;
      case NO_ABSTRACT_METHOD -> JavaErrorBundle.message("no.target.method.found");
      case MULTIPLE_ABSTRACT_METHODS ->
        JavaErrorBundle.message("multiple.non.overriding.abstract.methods.found.in.interface.0", HighlightUtil.formatClass(psiClass));
    };
  }

  static HighlightInfo.Builder checkParametersCompatible(@NotNull PsiLambdaExpression expression,
                                                 PsiParameter @NotNull [] methodParameters,
                                                 @NotNull PsiSubstitutor substitutor) {
    PsiParameter[] lambdaParameters = expression.getParameterList().getParameters();
    if (lambdaParameters.length != methodParameters.length) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(expression.getParameterList())
        .descriptionAndTooltip(JavaErrorBundle.message("incompatible.parameter.types.in.lambda.wrong.number.of.parameters", methodParameters.length, lambdaParameters.length));
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
            JavaErrorBundle.message("incompatible.parameter.types.in.lambda", expectedType, actualType));
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

  public static @NlsContexts.DetailedDescription String checkInterfaceFunctional(@NotNull PsiElement context, @NotNull PsiType functionalInterfaceType) {
    if (functionalInterfaceType instanceof PsiIntersectionType intersection) {
      Set<MethodSignature> signatures = new HashSet<>();
      Map<PsiType, MethodSignature> typeAndSignature = new HashMap<>();
      for (PsiType type : intersection.getConjuncts()) {
        if (checkInterfaceFunctional(context, type) == null) {
          MethodSignature signature = getFunction(PsiUtil.resolveClassInType(type));
          LOG.assertTrue(signature != null, type.getCanonicalText());
          signatures.add(signature);
          typeAndSignature.put(type, signature);
        }
      }
      PsiType baseType = typeAndSignature.entrySet().iterator().next().getKey();
      MethodSignature baseSignature = typeAndSignature.get(baseType);
      LambdaUtil.TargetMethodContainer baseContainer = getTargetMethod(baseType, baseSignature, baseType);
      if (baseContainer == null) {
        return JavaErrorBundle.message("no.target.method.found");
      }
      PsiMethod baseMethod = baseContainer.targetMethod;
      if (signatures.size() > 1) {
        for (Map.Entry<PsiType, MethodSignature> entry : typeAndSignature.entrySet()) {
          if (baseType == entry.getKey()) {
            continue;
          }
          LambdaUtil.TargetMethodContainer container = getTargetMethod(entry.getKey(), baseSignature, baseType);
          if (container == null) {
            return JavaErrorBundle.message("multiple.non.overriding.abstract.methods.found.in.0",
                                           functionalInterfaceType.getPresentableText());
          }
          if (!LambdaUtil.isLambdaSubsignature(baseMethod, baseType, container.targetMethod, entry.getKey()) ||
              !container.inheritor.hasModifier(JvmModifier.ABSTRACT)) {
            return JavaErrorBundle.message("multiple.non.overriding.abstract.methods.found.in.0",
                                           functionalInterfaceType.getPresentableText());
          }
        }
      }
      for (PsiType type : intersection.getConjuncts()) {
        if (typeAndSignature.containsKey(type)) {
          continue;
        }
        LambdaUtil.TargetMethodContainer container = getTargetMethod(type, baseSignature, baseType);
        if (container == null) {
          continue;
        }
        PsiMethod inheritor = container.inheritor;
        PsiMethod target = container.targetMethod;
        if (!inheritor.hasModifier(JvmModifier.ABSTRACT) && LambdaUtil.isLambdaSubsignature(baseMethod, baseType, target, type)) {
          return JavaErrorBundle.message("no.target.method.found");
        }
      }
      return null;
    }
    PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    PsiClass aClass = resolveResult.getElement();
    if (aClass != null) {
      if (aClass instanceof PsiTypeParameter) return null; //should be logged as cyclic inference
      MethodSignature functionalMethod = getFunction(aClass);
      if (functionalMethod != null && functionalMethod.getTypeParameters().length > 0) {
        return JavaErrorBundle
          .message("target.method.is.generic");
      }
      return checkInterfaceFunctional(aClass);
    }
    if (IncompleteModelUtil.isIncompleteModel(context) &&
        IncompleteModelUtil.isUnresolvedClassType(functionalInterfaceType)) {
      return null;
    }
    return JavaErrorBundle.message("not.a.functional.interface", functionalInterfaceType.getPresentableText());
  }

  static HighlightInfo.Builder checkConsistentParameterDeclaration(@NotNull PsiLambdaExpression expression) {
    PsiParameter[] parameters = expression.getParameterList().getParameters();
    if (parameters.length < 2) return null;
    boolean hasExplicitParameterTypes = hasExplicitType(parameters[0]);
    for (int i = 1; i < parameters.length; i++) {
      if (hasExplicitParameterTypes != hasExplicitType(parameters[i])) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
                            .descriptionAndTooltip(JavaErrorBundle.message("lambda.parameters.consistency.message"))
                            .range(expression.getParameterList());
      }
    }

    return null;
  }

  private static boolean hasExplicitType(@NotNull PsiParameter parameter) {
    PsiTypeElement typeElement = parameter.getTypeElement();
    return typeElement != null && !typeElement.isInferredType();
  }

  // 15.13 | 15.27
  // It is a compile-time error if any class or interface mentioned by either U or the function type of U
  // is not accessible from the class or interface in which the method reference expression appears.
  static HighlightInfo.Builder checkFunctionalInterfaceTypeAccessible(@NotNull Project project,
                                                                      @NotNull PsiFunctionalExpression expression,
                                                                      @NotNull PsiType functionalInterfaceType) {
    return checkFunctionalInterfaceTypeAccessible(project, expression, functionalInterfaceType, true);
  }

  private static HighlightInfo.Builder checkFunctionalInterfaceTypeAccessible(@NotNull Project project, @NotNull PsiFunctionalExpression expression,
                                                                              @NotNull PsiType functionalInterfaceType,
                                                                              boolean checkFunctionalTypeSignature) {
    PsiClassType.ClassResolveResult resolveResult =
      PsiUtil.resolveGenericsClassInType(PsiClassImplUtil.correctType(functionalInterfaceType, expression.getResolveScope()));
    PsiClass psiClass = resolveResult.getElement();
    if (psiClass == null) {
      return null;
    }
    if (PsiUtil.isAccessible(project, psiClass, expression, null)) {
      for (PsiType type : resolveResult.getSubstitutor().getSubstitutionMap().values()) {
        if (type != null) {
          HighlightInfo.Builder info = checkFunctionalInterfaceTypeAccessible(project, expression, type, false);
          if (info != null) {
            return info;
          }
        }
      }

      PsiMethod psiMethod = checkFunctionalTypeSignature ? LambdaUtil.getFunctionalInterfaceMethod(resolveResult) : null;
      if (psiMethod != null) {
        PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(psiMethod, resolveResult);
        for (PsiParameter parameter : psiMethod.getParameterList().getParameters()) {
          PsiType substitute = substitutor.substitute(parameter.getType());
          if (substitute != null) {
            HighlightInfo.Builder info = checkFunctionalInterfaceTypeAccessible(project, expression, substitute, false);
            if (info != null) {
              return info;
            }
          }
        }

        PsiType substitute = substitutor.substitute(psiMethod.getReturnType());
        if (substitute != null) {
          return checkFunctionalInterfaceTypeAccessible(project, expression, substitute, false);
        }
        return null;
      }
    }
    else {
      Pair<@Nls String, List<IntentionAction>> problem =
        HighlightUtil.accessProblemDescriptionAndFixes(expression, psiClass, resolveResult);
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(problem.first);
      if (problem.second != null) {
        problem.second.forEach(fix -> info.registerFix(fix, List.of(), null, null, null));
      }
      return info;
    }

    final ErrorWithFixes moduleProblem = HighlightUtil.checkModuleAccess(psiClass, expression, resolveResult);
    if (moduleProblem != null) {
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(moduleProblem.message);
      moduleProblem.fixes.forEach(fix -> info.registerFix(fix, List.of(), null, null, null));
      return info;
    }

    return null;
  }

  static boolean lambdaParametersMentionTypeParameter(@NotNull PsiType functionalInterfaceType, @NotNull Set<? extends PsiTypeParameter> parameters) {
    if (!(functionalInterfaceType instanceof PsiClassType classType)) return false;
    PsiSubstitutor substitutor = classType.resolveGenerics().getSubstitutor();
    PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
    if (method == null) return false;
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      if (PsiTypesUtil.mentionsTypeParameters(substitutor.substitute(parameter.getType()), parameters)) return true;
    }
    return false;
  }
}

/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * User: anna
 */
public class PsiOldInferenceHelper implements PsiInferenceHelper {
    private static final Logger LOG = Logger.getInstance("#" + PsiOldInferenceHelper.class.getName());
    public static final Pair<PsiType,ConstraintType> RAW_INFERENCE = new Pair<PsiType, ConstraintType>(null, ConstraintType.EQUALS);
    private final PsiManager myManager;
  
    public PsiOldInferenceHelper(PsiManager manager) {
      myManager = manager;
    }

    private Pair<PsiType, ConstraintType> inferTypeForMethodTypeParameterInner(@NotNull PsiTypeParameter typeParameter,
                                                                                    @NotNull PsiParameter[] parameters,
                                                                                    @NotNull PsiExpression[] arguments,
                                                                                    @NotNull PsiSubstitutor partialSubstitutor,
                                                                                    final PsiElement parent,
                                                                                    @NotNull ParameterTypeInferencePolicy policy) {
    PsiType[] paramTypes = new PsiType[arguments.length];
    PsiType[] argTypes = new PsiType[arguments.length];
    if (parameters.length > 0) {
      for (int j = 0; j < argTypes.length; j++) {
        final PsiExpression argument = arguments[j];
        if (argument == null) continue;
        if (argument instanceof PsiMethodCallExpression && PsiResolveHelper.ourGuard.currentStack().contains(argument)) continue;

        final RecursionGuard.StackStamp stackStamp = PsiDiamondType.ourDiamondGuard.markStack();
        argTypes[j] = argument.getType();
        if (!stackStamp.mayCacheNow()) {
          argTypes[j] = null;
          continue;
        }

        final PsiParameter parameter = parameters[Math.min(j, parameters.length - 1)];
        if (j >= parameters.length && !parameter.isVarArgs()) break;
        paramTypes[j] = parameter.getType();
        if (paramTypes[j] instanceof PsiEllipsisType) {
          paramTypes[j] = ((PsiEllipsisType)paramTypes[j]).getComponentType();
          if (arguments.length == parameters.length &&
              argTypes[j] instanceof PsiArrayType &&
              !(((PsiArrayType)argTypes[j]).getComponentType() instanceof PsiPrimitiveType)) {
            argTypes[j] = ((PsiArrayType)argTypes[j]).getComponentType();
          }
        }
      }
    }
    return inferTypeForMethodTypeParameterInner(typeParameter, paramTypes, argTypes, partialSubstitutor, parent, policy);
  }

  private Pair<PsiType, ConstraintType> inferTypeForMethodTypeParameterInner(@NotNull PsiTypeParameter typeParameter,
                                                                                    @NotNull PsiType[] paramTypes,
                                                                                    @NotNull PsiType[] argTypes,
                                                                                    @NotNull PsiSubstitutor partialSubstitutor,
                                                                                    @Nullable PsiElement parent,
                                                                                    @NotNull ParameterTypeInferencePolicy policy) {
    PsiWildcardType wildcardToCapture = null;
    Pair<PsiType, ConstraintType> rawInference = null;
    PsiType lowerBound = PsiType.NULL;
    PsiType upperBound = PsiType.NULL;
    if (paramTypes.length > 0) {
      sortLambdaExpressionsLast(paramTypes, argTypes);
      boolean rawType = false;
      boolean nullPassed = false;
      boolean lambdaRaw = false;
      for (int j = 0; j < argTypes.length; j++) {
        PsiType argumentType = argTypes[j];
        if (argumentType == null) continue;
        if (j >= paramTypes.length) break;

        PsiType parameterType = paramTypes[j];
        if (parameterType == null) break;
        rawType |= parameterType instanceof PsiClassType && ((PsiClassType)parameterType).isRaw();
        nullPassed |= argumentType == PsiType.NULL;

        if (parameterType instanceof PsiEllipsisType) {
          parameterType = ((PsiEllipsisType)parameterType).getComponentType();
          if (argTypes.length == paramTypes.length && argumentType instanceof PsiArrayType && !(((PsiArrayType)argumentType).getComponentType() instanceof PsiPrimitiveType)) {
            argumentType = ((PsiArrayType)argumentType).getComponentType();
          }
        }
        final Pair<PsiType,ConstraintType> currentSubstitution;
        if (argumentType instanceof PsiLambdaExpressionType) {
          currentSubstitution = inferSubstitutionFromLambda(typeParameter, (PsiLambdaExpressionType)argumentType, lowerBound, partialSubstitutor);
          if (rawType) {
            if (currentSubstitution == FAILED_INFERENCE || currentSubstitution == null && lowerBound == PsiType.NULL) return RAW_INFERENCE;
          }
          if (nullPassed && currentSubstitution == null) return RAW_INFERENCE;
          if (currentSubstitution != null && currentSubstitution.first == null) {
            lambdaRaw = true;
          }
          if (currentSubstitution == null && lambdaRaw) {
            return new Pair<PsiType, ConstraintType>(PsiType.getJavaLangObject(myManager, typeParameter.getResolveScope()), ConstraintType.EQUALS);
          }
        } else if (argumentType instanceof PsiMethodReferenceType) {
          final PsiMethodReferenceExpression referenceExpression = ((PsiMethodReferenceType)argumentType).getExpression();
          currentSubstitution = inferConstraintFromFunctionalInterfaceMethod(typeParameter, referenceExpression, partialSubstitutor.substitute(parameterType), partialSubstitutor, policy);
        }
        else {
          currentSubstitution = getSubstitutionForTypeParameterConstraint(typeParameter, parameterType,
                                                                          argumentType, true, PsiUtil.getLanguageLevel(typeParameter));
        }
        if (currentSubstitution == null) continue;
        if (currentSubstitution == FAILED_INFERENCE) {
          return getFailedInferenceConstraint(typeParameter);
        }

        final ConstraintType constraintType = currentSubstitution.getSecond();
        final PsiType type = currentSubstitution.getFirst();
        if (type == null) {
          rawInference = RAW_INFERENCE;
          continue;
        }
        switch(constraintType) {
          case EQUALS:
            if (!(type instanceof PsiWildcardType)) return currentSubstitution;
            if (wildcardToCapture != null) return getFailedInferenceConstraint(typeParameter);
            wildcardToCapture = (PsiWildcardType) type;
            break;
          case SUPERTYPE:
            if (PsiType.NULL.equals(lowerBound)) {
              lowerBound = type;
            }
            else if (!lowerBound.equals(type)) {
              lowerBound = GenericsUtil.getLeastUpperBound(lowerBound, type, myManager);
              if (lowerBound == null) return getFailedInferenceConstraint(typeParameter);
            }
            break;
          case SUBTYPE:
            if (PsiType.NULL.equals(upperBound) || TypeConversionUtil.isAssignable(upperBound, type)) {
              upperBound = type;
            }
        }
      }
    }

    if (wildcardToCapture != null) {
      if (lowerBound != PsiType.NULL) {
        if (!wildcardToCapture.isAssignableFrom(lowerBound)) return getFailedInferenceConstraint(typeParameter);
        if (wildcardToCapture.isSuper()) {
          return new Pair<PsiType, ConstraintType>(wildcardToCapture, ConstraintType.SUPERTYPE);
        }
        lowerBound = GenericsUtil.getLeastUpperBound(lowerBound, wildcardToCapture, myManager);
      }
      else {
        if (upperBound != PsiType.NULL && !upperBound.isAssignableFrom(wildcardToCapture)) return getFailedInferenceConstraint(typeParameter);
        return new Pair<PsiType, ConstraintType>(wildcardToCapture, ConstraintType.EQUALS);
      }
    }

    if (rawInference != null) return rawInference;
    if (lowerBound != PsiType.NULL) return new Pair<PsiType, ConstraintType>(lowerBound, ConstraintType.EQUALS);

    if (parent != null) {
      final Pair<PsiType, ConstraintType> constraint =
        inferMethodTypeParameterFromParent(typeParameter, partialSubstitutor, parent, policy);
      if (constraint != null) {
        if (constraint.getSecond() != ConstraintType.SUBTYPE) {
          return constraint;
        }

        if (upperBound != PsiType.NULL) {
          return new Pair<PsiType, ConstraintType>(upperBound, ConstraintType.SUBTYPE);
        }

        return constraint;
      }
    }

    if (upperBound != PsiType.NULL) return new Pair<PsiType, ConstraintType>(upperBound, ConstraintType.SUBTYPE);
    return null;
  }

  private static void sortLambdaExpressionsLast(@NotNull PsiType[] paramTypes, @NotNull PsiType[] argTypes) {
    for (int i = 0; i < argTypes.length; i++) {
      PsiType argType = argTypes[i];
      if ((argType instanceof PsiLambdaExpressionType || argType instanceof PsiMethodReferenceType) && i < argTypes.length - 1) {
        int k = i + 1;
        while((argTypes[k] instanceof PsiLambdaExpressionType || argTypes[k]  instanceof PsiMethodReferenceType) && k < argTypes.length - 1) {
          k++;
        }
        if (!(argTypes[k] instanceof PsiLambdaExpressionType || argTypes[k] instanceof PsiMethodReferenceType)) {
          ArrayUtil.swap(paramTypes, i, k);
          ArrayUtil.swap(argTypes, i, k);
          i = k;
        }
      }
    }
  }

  private static Pair<PsiType, ConstraintType> getFailedInferenceConstraint(@NotNull PsiTypeParameter typeParameter) {
    return new Pair<PsiType, ConstraintType>(JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(typeParameter), ConstraintType.EQUALS);
  }

  @Override
  public PsiType inferTypeForMethodTypeParameter(@NotNull final PsiTypeParameter typeParameter,
                                                 @NotNull final PsiParameter[] parameters,
                                                 @NotNull PsiExpression[] arguments,
                                                 @NotNull PsiSubstitutor partialSubstitutor,
                                                 PsiElement parent,
                                                 @NotNull final ParameterTypeInferencePolicy policy) {

    final Pair<PsiType, ConstraintType> constraint =
      inferTypeForMethodTypeParameterInner(typeParameter, parameters, arguments, partialSubstitutor, parent, policy);
    if (constraint == null) return PsiType.NULL;
    return constraint.getFirst();
  }

  @NotNull
  @Override
  public PsiSubstitutor inferTypeArguments(@NotNull PsiTypeParameter[] typeParameters,
                                           @NotNull PsiParameter[] parameters,
                                           @NotNull PsiExpression[] arguments,
                                           @NotNull PsiSubstitutor partialSubstitutor,
                                           @NotNull PsiElement parent,
                                           @NotNull ParameterTypeInferencePolicy policy,
                                           @NotNull LanguageLevel languageLevel) {
    PsiType[] substitutions = new PsiType[typeParameters.length];
    @SuppressWarnings("unchecked")
    Pair<PsiType, ConstraintType>[] constraints = new Pair[typeParameters.length];
    for (int i = 0; i < typeParameters.length; i++) {
      if (substitutions[i] != null) continue;
      final Pair<PsiType, ConstraintType> constraint =
        inferTypeForMethodTypeParameterInner(typeParameters[i], parameters, arguments, partialSubstitutor, null, policy);
      constraints[i] = constraint;
      if (constraint != null && constraint.getSecond() != ConstraintType.SUBTYPE) {
        substitutions[i] = constraint.getFirst();

        if (substitutions[i] != null && languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) { //try once more
          partialSubstitutor = partialSubstitutor.put(typeParameters[i], substitutions[i]);
          i = -1;
        }
      }
    }

    for (int i = 0; i < typeParameters.length; i++) {
      PsiTypeParameter typeParameter = typeParameters[i];
      if (substitutions[i] == null) {
        PsiType substitutionFromBounds = PsiType.NULL;
        OtherParameters:
        for (int j = 0; j < typeParameters.length; j++) {
          if (i != j) {
            PsiTypeParameter other = typeParameters[j];
            final PsiType otherSubstitution = substitutions[j];
            if (otherSubstitution == null) continue;
            final PsiClassType[] bounds = other.getExtendsListTypes();
            for (PsiClassType bound : bounds) {
              final PsiType substitutedBound = partialSubstitutor.substitute(bound);
              final Pair<PsiType, ConstraintType> currentConstraint =
                getSubstitutionForTypeParameterConstraint(typeParameter, substitutedBound, otherSubstitution, true, languageLevel);
              if (currentConstraint == null) continue;
              final PsiType currentSubstitution = currentConstraint.getFirst();
              final ConstraintType currentConstraintType = currentConstraint.getSecond();
              if (currentConstraintType == ConstraintType.EQUALS) {
                substitutionFromBounds = currentSubstitution;
                if (currentSubstitution == null) {
                  constraints[i] = FAILED_INFERENCE;
                }
                break OtherParameters;
              }
              else if (currentConstraintType == ConstraintType.SUPERTYPE) {
                if (PsiType.NULL.equals(substitutionFromBounds)) {
                  substitutionFromBounds = currentSubstitution;
                }
                else {
                  substitutionFromBounds = GenericsUtil.getLeastUpperBound(substitutionFromBounds, currentSubstitution, myManager);
                }
              }
            }

          }
        }

        if (substitutionFromBounds != PsiType.NULL) substitutions[i] = substitutionFromBounds;
      }
    }

    for (int i = 0; i < typeParameters.length; i++) {
      PsiTypeParameter typeParameter = typeParameters[i];
      PsiType substitution = substitutions[i];
      if (substitution != PsiType.NULL) {
        partialSubstitutor = partialSubstitutor.put(typeParameter, substitution);
      }
    }

    try {
      for (int i = 0; i < typeParameters.length; i++) {
        PsiTypeParameter typeParameter = typeParameters[i];
        PsiType substitution = substitutions[i];
        if (substitution != null) continue;

        Pair<PsiType, ConstraintType> constraint = constraints[i];
        if (constraint == null) {
          constraint = inferMethodTypeParameterFromParent(typeParameter, partialSubstitutor, parent, policy);
        }
        else if (constraint.getSecond() == ConstraintType.SUBTYPE) {
          Pair<PsiType, ConstraintType> otherConstraint = inferMethodTypeParameterFromParent(typeParameter, partialSubstitutor, parent, policy);
          if (otherConstraint != null) {
            if (otherConstraint.getSecond() == ConstraintType.EQUALS || otherConstraint.getSecond() == ConstraintType.SUPERTYPE) {
              constraint = otherConstraint;
            }
          }
        }

        if (constraint != null) {
          substitution = constraint.getFirst();
        }

        if (substitution == null) {
          PsiElementFactory factory = JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory();
          return factory.createRawSubstitutor(partialSubstitutor, typeParameters);
        }
        if (substitution != PsiType.NULL) {
          partialSubstitutor = partialSubstitutor.put(typeParameter, substitution);
        }
      }
    }
    finally {
      GraphInferencePolicy.forget(parent);
    }
    return partialSubstitutor;
  }

  @Override
  @NotNull
  public PsiSubstitutor inferTypeArguments(@NotNull PsiTypeParameter[] typeParameters,
                                           @NotNull PsiType[] leftTypes,
                                           @NotNull PsiType[] rightTypes,
                                           @NotNull LanguageLevel languageLevel) {
    if (leftTypes.length != rightTypes.length) throw new IllegalArgumentException("Types must be of the same length");
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter typeParameter : typeParameters) {
      PsiType substitution = PsiType.NULL;
      PsiType lowerBound = PsiType.NULL;
      for (int i1 = 0; i1 < leftTypes.length; i1++) {
        PsiType leftType = leftTypes[i1];
        PsiType rightType = rightTypes[i1];
        final Pair<PsiType, ConstraintType> constraint =
            getSubstitutionForTypeParameterConstraint(typeParameter, leftType, rightType, true, languageLevel);
        if (constraint != null) {
          final ConstraintType constraintType = constraint.getSecond();
          final PsiType current = constraint.getFirst();
          if (constraintType == ConstraintType.EQUALS) {
            substitution = current;
            break;
          }
          else if (constraintType == ConstraintType.SUBTYPE) {
            if (PsiType.NULL.equals(substitution)) {
              substitution = current;
            }
            else {
              substitution = GenericsUtil.getLeastUpperBound(substitution, current, myManager);
            }
          }
          else {
            if (PsiType.NULL.equals(lowerBound)) {
              lowerBound = current;
            }
            else {
              lowerBound = GenericsUtil.getLeastUpperBound(lowerBound, current, myManager);
            }
          }
        }
      }

      if (PsiType.NULL.equals(substitution)) {
        substitution = lowerBound;
      }

      if (substitution != PsiType.NULL) {
        substitutor = substitutor.put(typeParameter, substitution);
      }
    }
    for (int i = 0; i < typeParameters.length; i++) {
      PsiTypeParameter typeParameter = typeParameters[i];
      if (!substitutor.getSubstitutionMap().containsKey(typeParameter)) {
        PsiType substitutionFromBounds = PsiType.NULL;
        OtherParameters:
        for (int j = 0; j < typeParameters.length; j++) {
          if (i != j) {
            PsiTypeParameter other = typeParameters[j];
            final PsiType otherSubstitution = substitutor.substitute(other);
            if (otherSubstitution == null) continue;
            final PsiClassType[] bounds = other.getExtendsListTypes();
            for (PsiClassType bound : bounds) {
              final PsiType substitutedBound = substitutor.substitute(bound);
              final Pair<PsiType, ConstraintType> currentConstraint =
                getSubstitutionForTypeParameterConstraint(typeParameter, substitutedBound, otherSubstitution, true, languageLevel);
              if (currentConstraint == null) continue;
              final PsiType currentSubstitution = currentConstraint.getFirst();
              final ConstraintType currentConstraintType = currentConstraint.getSecond();
              if (currentConstraintType == ConstraintType.EQUALS) {
                substitutionFromBounds = currentSubstitution;
                break OtherParameters;
              }
              else if (currentConstraintType == ConstraintType.SUPERTYPE) {
                if (PsiType.NULL.equals(substitutionFromBounds)) {
                  substitutionFromBounds = currentSubstitution;
                }
                else {
                  substitutionFromBounds = GenericsUtil.getLeastUpperBound(substitutionFromBounds, currentSubstitution, myManager);
                }
              }
            }
          }
        }
        if (substitutionFromBounds != PsiType.NULL) {
          substitutor = substitutor.put(typeParameter, substitutionFromBounds);
        }
      }
    }
    return substitutor;
  }

  @Nullable
  private static Pair<PsiType, ConstraintType> processArgType(PsiType arg, final ConstraintType constraintType,
                                                              final boolean captureWildcard) {
    if (arg instanceof PsiWildcardType && !captureWildcard) return FAILED_INFERENCE;
    if (arg != PsiType.NULL) {
      return new Pair<PsiType, ConstraintType>(arg, constraintType);
    }
    return null;
  }

  private Pair<PsiType, ConstraintType> inferMethodTypeParameterFromParent(@NotNull PsiTypeParameter typeParameter,
                                                                                  @NotNull PsiSubstitutor substitutor,
                                                                                  @NotNull PsiElement parent,
                                                                                  @NotNull ParameterTypeInferencePolicy policy) {
    PsiTypeParameterListOwner owner = typeParameter.getOwner();
    Pair<PsiType, ConstraintType> substitution = null;
    if (owner instanceof PsiMethod && parent instanceof PsiCallExpression) {
      PsiCallExpression methodCall = (PsiCallExpression)parent;
      substitution = inferMethodTypeParameterFromParent(PsiUtil.skipParenthesizedExprUp(methodCall.getParent()), methodCall, typeParameter, substitutor, policy);
    }
    return substitution;
  }

  @Override
  public PsiType getSubstitutionForTypeParameter(PsiTypeParameter typeParam,
                                                 PsiType param,
                                                 PsiType arg,
                                                 boolean isContraVariantPosition,
                                                 final LanguageLevel languageLevel) {
    final Pair<PsiType, ConstraintType> constraint = getSubstitutionForTypeParameterConstraint(typeParam, param, arg, isContraVariantPosition,
                                                                                               languageLevel);
    return constraint == null ? PsiType.NULL : constraint.getFirst();
  }

  @Nullable
  public Pair<PsiType, ConstraintType> getSubstitutionForTypeParameterConstraint(PsiTypeParameter typeParam,
                                                                                 PsiType param,
                                                                                 PsiType arg,
                                                                                 boolean isContraVariantPosition,
                                                                                 final LanguageLevel languageLevel) {
    if (param instanceof PsiArrayType && arg instanceof PsiArrayType) {
      return getSubstitutionForTypeParameterConstraint(typeParam, ((PsiArrayType)param).getComponentType(), ((PsiArrayType)arg).getComponentType(),
                                             isContraVariantPosition, languageLevel);
    }

    if (!(param instanceof PsiClassType)) return null;
    PsiManager manager = myManager;
    if (arg instanceof PsiPrimitiveType) {
      if (!JavaVersionService.getInstance().isAtLeast(typeParam, JavaSdkVersion.JDK_1_7) && !isContraVariantPosition) return null;
      arg = ((PsiPrimitiveType)arg).getBoxedType(typeParam);
      if (arg == null) return null;
    }

    JavaResolveResult paramResult = ((PsiClassType)param).resolveGenerics();
    PsiClass paramClass = (PsiClass)paramResult.getElement();
    if (typeParam == paramClass) {
      final PsiClass psiClass = PsiUtil.resolveClassInType(arg);
      if (arg == null ||
          arg.getDeepComponentType() instanceof PsiPrimitiveType ||
          arg instanceof PsiIntersectionType ||
          (psiClass != null && (isContraVariantPosition || !CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName()) || (arg instanceof PsiArrayType)))) {
        PsiType bound = intersectAllExtends(typeParam, arg);
        return new Pair<PsiType, ConstraintType>(bound, ConstraintType.SUPERTYPE);
      }
      if (psiClass == null && arg instanceof PsiClassType) {
        return Pair.create(arg, ConstraintType.EQUALS);
      }
      return null;
    }
    if (paramClass == null) return null;

    if (!(arg instanceof PsiClassType)) return null;

    JavaResolveResult argResult = ((PsiClassType)arg).resolveGenerics();
    PsiClass argClass = (PsiClass)argResult.getElement();
    if (argClass == null) return null;

    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    PsiType patternType = factory.createType(typeParam);
    if (isContraVariantPosition) {
      PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(paramClass, argClass, argResult.getSubstitutor());
      if (substitutor == null) return null;
      arg = factory.createType(paramClass, substitutor, languageLevel);
    }
    else {
      PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(argClass, paramClass, paramResult.getSubstitutor());
      if (substitutor == null) return null;
      param = factory.createType(argClass, substitutor, languageLevel);
    }

    return getSubstitutionForTypeParameterInner(param, arg, patternType, ConstraintType.SUPERTYPE, 0);
  }

  @Nullable
  private Pair<PsiType, ConstraintType> inferSubstitutionFromLambda(PsiTypeParameter typeParam,
                                                                           PsiLambdaExpressionType arg,
                                                                           PsiType lowerBound,
                                                                           PsiSubstitutor partialSubstitutor) {
    final PsiLambdaExpression lambdaExpression = arg.getExpression();
    if (PsiUtil.getLanguageLevel(lambdaExpression).isAtLeast(LanguageLevel.JDK_1_8)) {
      final PsiElement parent = PsiUtil.skipParenthesizedExprUp(lambdaExpression.getParent());
      if (parent instanceof PsiExpressionList) {
        final PsiExpressionList expressionList = (PsiExpressionList)parent;
        final Map<PsiElement, Pair<PsiMethod, PsiSubstitutor>> methodMap = MethodCandidateInfo.CURRENT_CANDIDATE.get();
        final Pair<PsiMethod, PsiSubstitutor> pair = methodMap != null ? methodMap.get(expressionList) : null;
        if (pair != null) {
          final int i = LambdaUtil.getLambdaIdx(expressionList, lambdaExpression);
          if (i < 0) return null;
          final PsiParameter[] parameters = pair.first.getParameterList().getParameters();
          if (parameters.length <= i) return null;
          final PsiSubstitutor combinedSubst = pair.second.putAll(partialSubstitutor);
          methodMap.put(expressionList, Pair.create(pair.first, combinedSubst));
          return inferConstraintFromFunctionalInterfaceMethod(typeParam, lambdaExpression, combinedSubst.substitute(parameters[i].getType()), lowerBound);
        }
      }
      else {
        return inferConstraintFromFunctionalInterfaceMethod(typeParam, lambdaExpression,
                                                            partialSubstitutor.substitute(lambdaExpression.getFunctionalInterfaceType()), lowerBound);
      }
    }
    return null;
  }

  @Nullable
  private Pair<PsiType, ConstraintType> inferConstraintFromFunctionalInterfaceMethod(final PsiTypeParameter typeParam,
                                                                                            final PsiMethodReferenceExpression methodReferenceExpression,
                                                                                            final PsiType functionalInterfaceType,
                                                                                            final PsiSubstitutor partialSubstitutor,
                                                                                            final ParameterTypeInferencePolicy policy) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final PsiMethod functionalInterfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
    if (functionalInterfaceMethod != null) {
      final PsiSubstitutor subst = LambdaUtil.getSubstitutor(functionalInterfaceMethod, resolveResult);
      final PsiParameter[] methodParameters = functionalInterfaceMethod.getParameterList().getParameters();
      PsiType[] methodParamTypes = new PsiType[methodParameters.length];
      for (int i = 0; i < methodParameters.length; i++) {
        methodParamTypes[i] = GenericsUtil.eliminateWildcards(subst.substitute(methodParameters[i].getType()));
      }

      if (methodParamsDependOn(typeParam, methodReferenceExpression, functionalInterfaceType, methodParameters, subst)) {
        return null;
      }

      final PsiType[] args = new PsiType[methodParameters.length];
      Map<PsiMethodReferenceExpression,PsiType> map = PsiMethodReferenceUtil.ourRefs.get();
      if (map == null) {
        map = new HashMap<PsiMethodReferenceExpression, PsiType>();
        PsiMethodReferenceUtil.ourRefs.set(map);
      }
      final PsiType added = map.put(methodReferenceExpression, functionalInterfaceType);
      final JavaResolveResult methReferenceResolveResult;
      try {
        methReferenceResolveResult = methodReferenceExpression.advancedResolve(false);
      }
      finally {
        if (added == null) {
          map.remove(methodReferenceExpression);
        }
      }
      final PsiElement resolved = methReferenceResolveResult.getElement();
      if (resolved instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)resolved;
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        boolean hasReceiver = false;
        if (methodParamTypes.length == parameters.length + 1) {
          if (!PsiMethodReferenceUtil
            .isReceiverType(methodParamTypes[0], method.getContainingClass(), methReferenceResolveResult.getSubstitutor())) return null;
          hasReceiver = true;
        } else if (parameters.length != methodParameters.length) {
          return null;
        }
        for (int i = 0; i < parameters.length; i++) {
          args[i] = methReferenceResolveResult.getSubstitutor().substitute(subst.substitute(parameters[i].getType()));
        }

        final PsiType[] typesToInfer = hasReceiver ? ArrayUtil.remove(methodParamTypes, 0) : methodParamTypes;
        final Pair<PsiType, ConstraintType> constraint = inferTypeForMethodTypeParameterInner(typeParam, typesToInfer, args, subst, null, DefaultParameterTypeInferencePolicy.INSTANCE);
        if (constraint != null){
          return constraint;
        }
        PsiType functionalInterfaceReturnType = functionalInterfaceMethod.getReturnType();
        if (functionalInterfaceReturnType != null && functionalInterfaceReturnType != PsiType.VOID) {
          functionalInterfaceReturnType = GenericsUtil.eliminateWildcards(subst.substitute(functionalInterfaceReturnType));
          final PsiType argType;
          if (method.isConstructor()) {
            argType = JavaPsiFacade.getElementFactory(functionalInterfaceMethod.getProject()).createType(method.getContainingClass(), methReferenceResolveResult.getSubstitutor());
          } else {
            argType = methReferenceResolveResult.getSubstitutor().substitute(subst.substitute(method.getReturnType()));
          }
          final Pair<PsiType, ConstraintType> typeParameterConstraint =
            getSubstitutionForTypeParameterConstraint(typeParam, functionalInterfaceReturnType, argType, true, PsiUtil.getLanguageLevel(functionalInterfaceMethod));
          if (typeParameterConstraint != null && typeParameterConstraint.getSecond() != ConstraintType.EQUALS && method.isConstructor()) {
            final Pair<PsiType, ConstraintType> constraintFromParent =
              inferMethodTypeParameterFromParent(typeParam, partialSubstitutor, methodReferenceExpression.getParent().getParent(), policy);
            if (constraintFromParent != null && constraintFromParent.getSecond() == ConstraintType.EQUALS) return constraintFromParent;
          }
          return typeParameterConstraint;
        }
      }
    }
    return null;
  }

  @Nullable
  private Pair<PsiType, ConstraintType> inferConstraintFromFunctionalInterfaceMethod(PsiTypeParameter typeParam,
                                                                                            final PsiLambdaExpression lambdaExpression,
                                                                                            final PsiType functionalInterfaceType,
                                                                                            PsiType lowerBound) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
    if (method != null) {
      final PsiSubstitutor subst = LambdaUtil.getSubstitutor(method, resolveResult);
      final Pair<PsiType, ConstraintType> constraintFromFormalParams = inferConstraintFromLambdaFormalParams(typeParam, subst, method, lambdaExpression);
      if (constraintFromFormalParams != null) return constraintFromFormalParams;

      final PsiParameter[] methodParameters = method.getParameterList().getParameters();
      if (methodParamsDependOn(typeParam, lambdaExpression, functionalInterfaceType, methodParameters, subst)) {
        return null;
      }

      final PsiType returnType = subst.substitute(method.getReturnType());
      if (returnType != null && returnType != PsiType.VOID) {
        Pair<PsiType, ConstraintType> constraint = null;
        final List<PsiExpression> expressions = LambdaUtil.getReturnExpressions(lambdaExpression);
        for (final PsiExpression expression : expressions) {
          final boolean independent = lambdaExpression.hasFormalParameterTypes() || LambdaUtil.isFreeFromTypeInferenceArgs(methodParameters, lambdaExpression, expression, subst, functionalInterfaceType, typeParam);
          if (!independent) {
            if (lowerBound != PsiType.NULL) {
              return null;
            }
            continue;
          }
          if (expression instanceof PsiReferenceExpression && ((PsiReferenceExpression)expression).resolve() == null) continue;
          PsiType exprType = PsiResolveHelper.ourGraphGuard.doPreventingRecursion(expression, true, new Computable<PsiType>() {
            @Override
            public PsiType compute() {
              return expression.getType();
            }
          });
          if (exprType instanceof PsiLambdaParameterType) {
            final PsiParameter parameter = ((PsiLambdaParameterType)exprType).getParameter();
            final int parameterIndex = lambdaExpression.getParameterList().getParameterIndex(parameter);
            if (parameterIndex > -1) {
              exprType = subst.substitute(methodParameters[parameterIndex].getType());
            }
          } else if (exprType instanceof PsiLambdaExpressionType) {
            return inferConstraintFromFunctionalInterfaceMethod(typeParam, ((PsiLambdaExpressionType)exprType).getExpression(), returnType,
                                                                lowerBound);
          } else if (exprType == null && independent) {
            return null;
          }

          if (exprType == null){
            return FAILED_INFERENCE;
          }

          final Pair<PsiType, ConstraintType> returnExprConstraint =
            getSubstitutionForTypeParameterConstraint(typeParam, GenericsUtil.eliminateWildcards(returnType), exprType, true, PsiUtil.getLanguageLevel(method));
          if (returnExprConstraint != null) {
            if (returnExprConstraint == FAILED_INFERENCE) return returnExprConstraint;
            if (constraint != null) {
              final PsiType leastUpperBound = GenericsUtil.getLeastUpperBound(constraint.getFirst(), returnExprConstraint.getFirst(), myManager);
              constraint = new Pair<PsiType, ConstraintType>(leastUpperBound, ConstraintType.SUPERTYPE);
            } else {
              constraint = returnExprConstraint;
            }
          }
        }
        if (constraint != null) return constraint;
      }
    }
    return null;
  }

  private static boolean methodParamsDependOn(PsiTypeParameter typeParam, PsiElement psiElement,
                                              PsiType functionalInterfaceType,
                                              PsiParameter[] methodParameters,
                                              PsiSubstitutor subst) {
    for (PsiParameter parameter : methodParameters) {
      if (LambdaUtil.dependsOnTypeParams(subst.substitute(parameter.getType()), functionalInterfaceType, psiElement, typeParam)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private Pair<PsiType, ConstraintType> inferConstraintFromLambdaFormalParams(PsiTypeParameter typeParam,
                                                                                     PsiSubstitutor subst,
                                                                                     PsiMethod method, PsiLambdaExpression lambdaExpression) {
    final PsiParameter[] parameters = lambdaExpression.getParameterList().getParameters();
    if (parameters.length == 0) return null;
    final PsiType[] lambdaArgs = new PsiType[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      if (parameter.getTypeElement() == null) {
        return null;
      }
      lambdaArgs[i] = parameter.getType();
    }

    final PsiParameter[] methodParameters = method.getParameterList().getParameters();
    PsiType[] methodParamTypes = new PsiType[methodParameters.length];
    for (int i = 0; i < methodParameters.length; i++) {
      methodParamTypes[i] = GenericsUtil.eliminateWildcards(subst.substitute(methodParameters[i].getType()));
    }
    return inferTypeForMethodTypeParameterInner(typeParam, methodParamTypes, lambdaArgs, subst, null, DefaultParameterTypeInferencePolicy.INSTANCE);
  }

  private static PsiType intersectAllExtends(PsiTypeParameter typeParam, PsiType arg) {
    if (arg == null) return null;
    PsiClassType[] superTypes = typeParam.getSuperTypes();
    PsiType[] erasureTypes = new PsiType[superTypes.length];
    for (int i = 0; i < superTypes.length; i++) {
      erasureTypes[i] = TypeConversionUtil.erasure(superTypes[i]);
    }
    PsiType[] types = ArrayUtil.append(erasureTypes, arg, PsiType.class);
    assert types.length != 0;
    return PsiIntersectionType.createIntersection(types);
  }

  //represents the result of failed type inference: in case we failed inferring from parameters, do not perform inference from context
  private static final Pair<PsiType, ConstraintType> FAILED_INFERENCE = new Pair<PsiType, ConstraintType>(PsiType.NULL, ConstraintType.EQUALS);

  @Nullable
  private Pair<PsiType, ConstraintType> getSubstitutionForTypeParameterInner(PsiType param,
                                                                                    PsiType arg,
                                                                                    PsiType patternType,
                                                                                    final ConstraintType constraintType,
                                                                                    final int depth) {
    if (arg instanceof PsiCapturedWildcardType && (depth < 2 || constraintType != ConstraintType.EQUALS)) arg = ((PsiCapturedWildcardType)arg).getWildcard(); //reopen

    if (patternType.equals(param)) {
      return processArgType(arg, constraintType, depth < 2);
    }

    if (param instanceof PsiWildcardType) {
      final PsiWildcardType wildcardParam = (PsiWildcardType)param;
      final PsiType paramBound = wildcardParam.getBound();
      if (paramBound == null) return null;
      ConstraintType constrType = wildcardParam.isExtends() ? ConstraintType.SUPERTYPE : ConstraintType.SUBTYPE;
      if (arg instanceof PsiWildcardType) {
        if (((PsiWildcardType)arg).isExtends() == wildcardParam.isExtends() && ((PsiWildcardType)arg).isBounded() == wildcardParam.isBounded()) {
          Pair<PsiType, ConstraintType> res = getSubstitutionForTypeParameterInner(paramBound, ((PsiWildcardType)arg).getBound(),
                                                                                   patternType, constrType, depth);
          if (res != null) return res;
        }
      }
      else if (patternType.equals(paramBound)) {
        Pair<PsiType, ConstraintType> res = getSubstitutionForTypeParameterInner(paramBound, arg,
                                                                                   patternType, constrType, depth);
        if (res != null) return res;
      }
      else if (paramBound instanceof PsiArrayType && arg instanceof PsiArrayType) {
        Pair<PsiType, ConstraintType> res = getSubstitutionForTypeParameterInner(((PsiArrayType) paramBound).getComponentType(),
                                                                                 ((PsiArrayType) arg).getComponentType(),
                                                                                 patternType, constrType, depth);
        if (res != null) return res;
      }
      else if (paramBound instanceof PsiClassType && arg instanceof PsiClassType) {
        final PsiClassType.ClassResolveResult boundResult = ((PsiClassType)paramBound).resolveGenerics();
        final PsiClass boundClass = boundResult.getElement();
        if (boundClass != null) {
          final PsiClassType.ClassResolveResult argResult = ((PsiClassType)arg).resolveGenerics();
          final PsiClass argClass = argResult.getElement();
          if (argClass != null) {
            if (wildcardParam.isExtends()) {
              PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(boundClass, argClass, argResult.getSubstitutor());
              if (superSubstitutor != null) {
                for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(boundClass)) {
                  PsiType substituted = superSubstitutor.substitute(typeParameter);
                  if (substituted != null) {
                    Pair<PsiType, ConstraintType> res = getSubstitutionForTypeParameterInner(
                      boundResult.getSubstitutor().substitute(typeParameter), substituted, patternType, ConstraintType.EQUALS, depth + 1);
                    if (res != null) return res;
                  }
                }
              }
            }
            else {
              PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(argClass, boundClass, boundResult.getSubstitutor());
              if (superSubstitutor != null) {
                for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(argClass)) {
                  PsiType substituted = argResult.getSubstitutor().substitute(typeParameter);
                  if (substituted != null) {
                    Pair<PsiType, ConstraintType> res = getSubstitutionForTypeParameterInner(
                      superSubstitutor.substitute(typeParameter), substituted, patternType, ConstraintType.EQUALS, depth + 1);
                    if (res != null) {
                      if (res == FAILED_INFERENCE) continue;
                      return res;
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    if (param instanceof PsiArrayType && arg instanceof PsiArrayType) {
      return getSubstitutionForTypeParameterInner(((PsiArrayType)param).getComponentType(), ((PsiArrayType)arg).getComponentType(),
                                                  patternType, constraintType, depth);
    }

    if (param instanceof PsiClassType && arg instanceof PsiClassType) {
      PsiClassType.ClassResolveResult paramResult = ((PsiClassType)param).resolveGenerics();
      PsiClass paramClass = paramResult.getElement();
      if (paramClass == null) return null;

      PsiClassType.ClassResolveResult argResult = ((PsiClassType)arg).resolveGenerics();
      PsiClass argClass = argResult.getElement();
      if (argClass != paramClass) {
        return inferBySubtypingConstraint(patternType, constraintType, depth, paramClass, argClass);
      }

      PsiType lowerBound = PsiType.NULL;
      PsiType upperBound = PsiType.NULL;
      Pair<PsiType,ConstraintType> wildcardCaptured = null;
      for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(paramClass)) {
        PsiType paramType = paramResult.getSubstitutor().substitute(typeParameter);
        PsiType argType = argResult.getSubstitutor().substituteWithBoundsPromotion(typeParameter);

        if (wildcardCaptured != null) {
          boolean alreadyFound = false;
          for (PsiTypeParameter typeParam : PsiUtil.typeParametersIterable(paramClass)) {
            if (typeParam != typeParameter &&
                paramType != null &&
                argResult.getSubstitutor().substituteWithBoundsPromotion(typeParam) == argType &&
                paramType.equals(paramResult.getSubstitutor().substitute(typeParam))) {
              alreadyFound = true;
            }
          }
          if (alreadyFound) continue;
        }

        Pair<PsiType,ConstraintType> res = getSubstitutionForTypeParameterInner(paramType, argType, patternType, ConstraintType.EQUALS, depth + 1);

        if (res != null) {
          final PsiType type = res.getFirst();
          switch (res.getSecond()) {
            case EQUALS:
              if (!(type instanceof PsiWildcardType)) return res;
              if (wildcardCaptured != null) return FAILED_INFERENCE;
              wildcardCaptured = res;
              break;
            case SUPERTYPE:
              wildcardCaptured = res;
              if (PsiType.NULL.equals(lowerBound)) {
                lowerBound = type;
              }
              else if (!lowerBound.equals(type)) {
                lowerBound = GenericsUtil.getLeastUpperBound(lowerBound, type, myManager);
                if (lowerBound == null) return FAILED_INFERENCE;
              }
              break;
            case SUBTYPE:
              wildcardCaptured = res;
              if (PsiType.NULL.equals(upperBound) || TypeConversionUtil.isAssignable(upperBound, type)) {
                upperBound = type;
              }
          }
        }
      }

      if (lowerBound != PsiType.NULL) return new Pair<PsiType, ConstraintType>(lowerBound, ConstraintType.SUPERTYPE);
      if (upperBound != PsiType.NULL) return new Pair<PsiType, ConstraintType>(upperBound, ConstraintType.SUBTYPE);

      return wildcardCaptured;
    }

    return null;
  }

  private static final Key<Boolean> inferSubtyping = Key.create("infer.subtyping.marker");
  private Pair<PsiType, ConstraintType> inferBySubtypingConstraint(PsiType patternType,
                                                                          ConstraintType constraintType,
                                                                          int depth,
                                                                          PsiClass paramClass,
                                                                          PsiClass argClass) {
    if (argClass instanceof PsiTypeParameter && paramClass instanceof PsiTypeParameter && PsiUtil.isLanguageLevel8OrHigher(argClass)) {
      final Boolean alreadyInferBySubtyping = paramClass.getCopyableUserData(inferSubtyping);
      if (alreadyInferBySubtyping != null) return null;
      final PsiClassType[] argExtendsListTypes = argClass.getExtendsListTypes();
      final PsiClassType[] paramExtendsListTypes = paramClass.getExtendsListTypes();
      if (argExtendsListTypes.length == paramExtendsListTypes.length) {
        try {
          paramClass.putCopyableUserData(inferSubtyping, true);
          for (int i = 0; i < argExtendsListTypes.length; i++) {
            PsiClassType argBoundType = argExtendsListTypes[i];
            PsiClassType paramBoundType = paramExtendsListTypes[i];
            final PsiClassType.ClassResolveResult argResolveResult = argBoundType.resolveGenerics();
            final PsiClassType.ClassResolveResult paramResolveResult = paramBoundType.resolveGenerics();
            final PsiClass paramBoundClass = paramResolveResult.getElement();
            final PsiClass argBoundClass = argResolveResult.getElement();
            if (argBoundClass != null && paramBoundClass != null && paramBoundClass != argBoundClass) {
              if (argBoundClass.isInheritor(paramBoundClass, true)) {
                final PsiSubstitutor superClassSubstitutor =
                  TypeConversionUtil.getSuperClassSubstitutor(paramBoundClass, argBoundClass, argResolveResult.getSubstitutor());
                argBoundType = JavaPsiFacade.getElementFactory(argClass.getProject()).createType(paramBoundClass, superClassSubstitutor);
              } else {
                return null;
              }
            }
            final Pair<PsiType, ConstraintType> constraint =
              getSubstitutionForTypeParameterInner(paramBoundType, argBoundType, patternType, constraintType, depth);
            if (constraint != null) {
              return constraint;
            }
          }
        }
        finally {
          paramClass.putCopyableUserData(inferSubtyping, null);
        }
      }
    }
    return null;
  }

  private Pair<PsiType, ConstraintType> inferMethodTypeParameterFromParent(@NotNull final PsiElement parent,
                                                                                  @NotNull PsiExpression methodCall,
                                                                                  @NotNull PsiTypeParameter typeParameter,
                                                                                  @NotNull PsiSubstitutor substitutor,
                                                                                  @NotNull ParameterTypeInferencePolicy policy) {
    Pair<PsiType, ConstraintType> constraint = null;
    PsiType expectedType = PsiTypesUtil.getExpectedTypeByParent(methodCall);

    if (expectedType == null) {
      if (parent instanceof PsiReturnStatement) {
        final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(parent, PsiLambdaExpression.class);
        if (lambdaExpression != null) {
          return getFailedInferenceConstraint(typeParameter);
        }
      }
      else if (parent instanceof PsiExpressionList) {
        final PsiElement pParent = parent.getParent();
        if (pParent instanceof PsiCallExpression && parent.equals(((PsiCallExpression)pParent).getArgumentList())) {
          constraint = policy.inferTypeConstraintFromCallContext(methodCall, (PsiExpressionList)parent, (PsiCallExpression)pParent, typeParameter);
          if (constraint == null && PsiUtil.isLanguageLevel8OrHigher(methodCall)) {
            constraint = graphInferenceFromCallContext(methodCall, typeParameter, (PsiCallExpression)pParent);
            if (constraint != null) {
              final PsiType constraintFirst = constraint.getFirst();
              if (constraintFirst == null || constraintFirst.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
                constraint = null;
              }
            }
          }
        }
      } else if (parent instanceof PsiLambdaExpression) {
        expectedType = PsiResolveHelper.ourGraphGuard.doPreventingRecursion(methodCall, true, new Computable<PsiType>() {
          @Override
          public PsiType compute() {
            return LambdaUtil.getFunctionalInterfaceReturnType(((PsiLambdaExpression)parent).getFunctionalInterfaceType());
          }
        });
        if (expectedType == null) {
          return null;
        }
        expectedType = GenericsUtil.eliminateWildcards(expectedType);
      } else if (parent instanceof PsiConditionalExpression) {
        if (PsiUtil.isLanguageLevel8OrHigher(parent)) {
          try {
            final Pair<PsiType, ConstraintType> pair = inferFromConditionalExpression(parent, methodCall, typeParameter, substitutor, policy);
            if (pair != null) {
              return pair;
            }
          }
          finally {
            GraphInferencePolicy.forget(parent);
          }
        }
      }
    }

    final GlobalSearchScope scope = parent.getResolveScope();
    PsiType returnType = null;
    if (constraint == null) {
      if (expectedType == null) {
        expectedType = methodCall instanceof PsiCallExpression ? policy.getDefaultExpectedType((PsiCallExpression)methodCall) : null;
      }

      returnType = ((PsiMethod)typeParameter.getOwner()).getReturnType();

      constraint =
        getSubstitutionForTypeParameterConstraint(typeParameter, returnType, expectedType, false, PsiUtil.getLanguageLevel(parent));

      if (constraint != null) {
        PsiType guess = constraint.getFirst();
        if (guess != null &&
            !guess.equals(PsiType.NULL) &&
            constraint.getSecond() == ConstraintType.SUPERTYPE &&
            guess instanceof PsiIntersectionType) {
          for (PsiType conjuct : ((PsiIntersectionType)guess).getConjuncts()) {
            if (!conjuct.isAssignableFrom(expectedType)) {
              return FAILED_INFERENCE;
            }
          }
        }
      }
    }

    if (constraint == null) {
      if (methodCall instanceof PsiCallExpression) {
        final PsiExpressionList argumentList = ((PsiCallExpression)methodCall).getArgumentList();
        if (argumentList != null && PsiUtil.getLanguageLevel(argumentList).isAtLeast(LanguageLevel.JDK_1_8)) {
          for (PsiExpression expression : argumentList.getExpressions()) {
            if (expression instanceof PsiLambdaExpression || expression instanceof PsiMethodReferenceExpression) {
              final PsiType functionalInterfaceType = LambdaUtil.getFunctionalInterfaceType(expression, false);
              if (functionalInterfaceType == null || PsiUtil.resolveClassInType(functionalInterfaceType) == typeParameter){
                return getFailedInferenceConstraint(typeParameter);
              }
              final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);

              final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
              if (method == null || methodParamsDependOn(typeParameter, expression,
                                                         functionalInterfaceType, method.getParameterList().getParameters(),
                                                         LambdaUtil.getSubstitutor(method, resolveResult))) {
                if (expression instanceof PsiMethodReferenceExpression) {
                  return getFailedInferenceConstraint(typeParameter);
                }
                return null;
              }
              final Pair<PsiType, ConstraintType> inferredExceptionTypeConstraint = inferExceptionConstrains(typeParameter, expression, method, resolveResult.getSubstitutor());
              if (inferredExceptionTypeConstraint != null) {
                return inferredExceptionTypeConstraint;
              }
            }
          }
        }

        PsiType[] superTypes = typeParameter.getSuperTypes();
        if (superTypes.length == 0) return null;
        final PsiType[] types = new PsiType[superTypes.length];
        for (int i = 0; i < superTypes.length; i++) {
          PsiType superType = substitutor.substitute(superTypes[i]);
          if (superType instanceof PsiClassType && ((PsiClassType)superType).isRaw()) {
            superType = TypeConversionUtil.erasure(superType);
          }
          if (superType == null) superType = PsiType.getJavaLangObject(myManager, scope);
          if (superType == null) return null;
          types[i] = superType;
        }
        return policy.getInferredTypeWithNoConstraint(myManager, PsiIntersectionType.createIntersection(types));
      }
      return null;
    }
    PsiType guess = constraint.getFirst();
    guess = policy.adjustInferredType(myManager, guess, constraint.getSecond());

    //The following code is the result of deep thought, do not shit it out before discussing with [ven]
    if (returnType instanceof PsiClassType && typeParameter.equals(((PsiClassType)returnType).resolve())) {
      PsiClassType[] extendsTypes = typeParameter.getExtendsListTypes();
      PsiSubstitutor newSubstitutor = substitutor.put(typeParameter, guess);
      for (PsiClassType extendsType1 : extendsTypes) {
        PsiType extendsType = newSubstitutor.substitute(extendsType1);
        if (guess != null && !extendsType.isAssignableFrom(guess)) {
          if (guess.isAssignableFrom(extendsType)) {
            guess = extendsType;
            newSubstitutor = substitutor.put(typeParameter, guess);
          }
          else {
            break;
          }
        }
      }
    }

    return new Pair<PsiType, ConstraintType>(guess, constraint.getSecond());
  }

  private static boolean checkSameExpression(PsiExpression templateExpr, final PsiExpression expression) {
    return templateExpr.equals(PsiUtil.skipParenthesizedExprDown(expression));
  }

  private static Pair<PsiType, ConstraintType> inferExceptionConstrains(PsiTypeParameter typeParameter,
                                                                        PsiExpression expression,
                                                                        PsiMethod method,
                                                                        PsiSubstitutor substitutor) {
    final PsiClassType[] declaredExceptions = method.getThrowsList().getReferencedTypes();
    for (PsiClassType exception : declaredExceptions) {
      final PsiType substitute = substitutor.substitute(exception);
      if (PsiUtil.resolveClassInType(substitute) == typeParameter) {
        if (expression instanceof PsiLambdaExpression) {
          final PsiElement body = ((PsiLambdaExpression)expression).getBody();
          if (body != null) {
            final List<PsiClassType> unhandledExceptions = ExceptionUtil.getUnhandledExceptions(body);
            if (unhandledExceptions.isEmpty()) {
              return inferUncheckedException(typeParameter, exception, method);
            }
          }
        }
        else if (expression instanceof PsiMethodReferenceExpression) {
          final PsiElement resolve = ((PsiMethodReferenceExpression)expression).resolve();
          if (resolve instanceof PsiMethod) {
            final PsiClassType[] declaredThrowsList = ((PsiMethod)resolve).getThrowsList().getReferencedTypes();
            for (PsiClassType psiClassType : declaredThrowsList) {
              if (!ExceptionUtil.isUncheckedException(psiClassType)) return null;
            }
            return inferUncheckedException(typeParameter, exception, method);
          }
        }
        break;
      }
    }
    return null;
  }

  private static Pair<PsiType, ConstraintType> inferUncheckedException(PsiTypeParameter typeParameter,
                                                                       PsiClassType exception,
                                                                       PsiMethod method) {
    final Project project = typeParameter.getProject();
    final PsiClass runtimeException = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION, method.getResolveScope());
    if (runtimeException != null) {
      for (PsiType superType : exception.getSuperTypes()) {
        if (!InheritanceUtil.isInheritorOrSelf(runtimeException, PsiUtil.resolveClassInType(superType), true)) {
          return getFailedInferenceConstraint(typeParameter);
        }
      }
      return Pair.<PsiType, ConstraintType>create(JavaPsiFacade.getElementFactory(project).createType(runtimeException, PsiSubstitutor.EMPTY), ConstraintType.EQUALS);
    }
    return null;
  }

  private Pair<PsiType, ConstraintType> inferFromConditionalExpression(@NotNull PsiElement parent,
                                                                              @NotNull PsiExpression methodCall,
                                                                              @NotNull PsiTypeParameter typeParameter,
                                                                              @NotNull PsiSubstitutor substitutor,
                                                                              @NotNull ParameterTypeInferencePolicy policy) {
    Pair<PsiType, ConstraintType> pair =
      inferMethodTypeParameterFromParent(PsiUtil.skipParenthesizedExprUp(parent.getParent()), (PsiExpression)parent, typeParameter, substitutor, policy);
    if (pair == null) {
      final PsiExpression thenExpression = ((PsiConditionalExpression)parent).getThenExpression();
      final PsiExpression elseExpression = ((PsiConditionalExpression)parent).getElseExpression();
      final PsiType[] paramTypes = {((PsiMethod)typeParameter.getOwner()).getReturnType()};
      if (methodCall.equals(PsiUtil.skipParenthesizedExprDown(elseExpression)) && thenExpression != null) {
        final PsiType thenType = PsiResolveHelper.ourGraphGuard.doPreventingRecursion(parent, true, new Computable<PsiType>() {
          @Override
          public PsiType compute() {
            return thenExpression.getType();
          }
        });
        if (thenType != null) {
          pair = inferTypeForMethodTypeParameterInner(typeParameter, paramTypes, new PsiType[] {thenType}, substitutor, null, policy);
        }
      } else if (methodCall.equals(PsiUtil.skipParenthesizedExprDown(thenExpression)) && elseExpression != null) {
        final PsiType elseType = PsiResolveHelper.ourGraphGuard.doPreventingRecursion(parent, true, new Computable<PsiType>() {
          @Override
          public PsiType compute() {
            return elseExpression.getType();
          }
        });
        if (elseType != null) {
          pair = inferTypeForMethodTypeParameterInner(typeParameter, paramTypes, new PsiType[] {elseType}, substitutor, null, policy);
        }
      }
    }
    return pair;
  }

  private static final ProcessCandidateParameterTypeInferencePolicy GRAPH_INFERENCE_POLICY = new GraphInferencePolicy();

  private static Pair<PsiType, ConstraintType> graphInferenceFromCallContext(@NotNull final PsiExpression methodCall,
                                                                             @NotNull final PsiTypeParameter typeParameter,
                                                                             @NotNull final PsiCallExpression parentCall) {
    if (Registry.is("disable.graph.inference", false)) return null;
    final PsiExpressionList argumentList = parentCall.getArgumentList();
    if (PsiDiamondType.ourDiamondGuard.currentStack().contains(parentCall)) {
      PsiDiamondType.ourDiamondGuard.prohibitResultCaching(parentCall);
      return FAILED_INFERENCE;
    }
    return PsiResolveHelper.ourGraphGuard.doPreventingRecursion(methodCall, true, new Computable<Pair<PsiType, ConstraintType>>() {
      @Override
      public Pair<PsiType, ConstraintType> compute() {
        return GRAPH_INFERENCE_POLICY.inferTypeConstraintFromCallContext(methodCall, argumentList, parentCall, typeParameter);
      }
    });
  }

}

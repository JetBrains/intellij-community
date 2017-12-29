// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiOldInferenceHelper implements PsiInferenceHelper {
  private static final Logger LOG = Logger.getInstance(PsiOldInferenceHelper.class);
    public static final Pair<PsiType,ConstraintType> RAW_INFERENCE = new Pair<>(null, ConstraintType.EQUALS);
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
      PsiType[] paramTypes = PsiType.createArray(arguments.length);
      PsiType[] argTypes = PsiType.createArray(arguments.length);
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
      for (int j = 0; j < argTypes.length; j++) {
        PsiType argumentType = argTypes[j];
        if (argumentType == null) continue;
        if (j >= paramTypes.length) break;

        PsiType parameterType = paramTypes[j];
        if (parameterType == null) break;

        if (parameterType instanceof PsiEllipsisType) {
          parameterType = ((PsiEllipsisType)parameterType).getComponentType();
          if (argTypes.length == paramTypes.length && argumentType instanceof PsiArrayType && !(((PsiArrayType)argumentType).getComponentType() instanceof PsiPrimitiveType)) {
            argumentType = ((PsiArrayType)argumentType).getComponentType();
          }
        }
        final Pair<PsiType,ConstraintType> currentSubstitution;
        currentSubstitution = getSubstitutionForTypeParameterConstraint(typeParameter, parameterType,
                                                                        argumentType, true, PsiUtil.getLanguageLevel(typeParameter));
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
          return new Pair<>(wildcardToCapture, ConstraintType.SUPERTYPE);
        }
        lowerBound = GenericsUtil.getLeastUpperBound(lowerBound, wildcardToCapture, myManager);
      }
      else {
        if (upperBound != PsiType.NULL && !upperBound.isAssignableFrom(wildcardToCapture)) return getFailedInferenceConstraint(typeParameter);
        return new Pair<>(wildcardToCapture, ConstraintType.EQUALS);
      }
    }

    if (rawInference != null) return rawInference;
    if (lowerBound != PsiType.NULL) return Pair.create(lowerBound, ConstraintType.EQUALS);

    if (parent != null) {
      final Pair<PsiType, ConstraintType> constraint =
        inferMethodTypeParameterFromParent(typeParameter, partialSubstitutor, parent, policy);
      if (constraint != null) {
        if (constraint.getSecond() != ConstraintType.SUBTYPE) {
          return constraint;
        }

        if (upperBound != PsiType.NULL) {
          return Pair.create(upperBound, ConstraintType.SUBTYPE);
        }

        return constraint;
      }
    }

    if (upperBound != PsiType.NULL) return Pair.create(upperBound, ConstraintType.SUBTYPE);
    return null;
  }

  private static Pair<PsiType, ConstraintType> getFailedInferenceConstraint(@NotNull PsiTypeParameter typeParameter) {
    return new Pair<>(JavaPsiFacade.getInstance(typeParameter.getProject()).getElementFactory().createType(typeParameter),
                      ConstraintType.EQUALS);
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
    PsiType[] substitutions = PsiType.createArray(typeParameters.length);
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
              else if (currentConstraintType == ConstraintType.SUPERTYPE && !JavaVersionService.getInstance().isAtLeast(parent, JavaSdkVersion.JDK_1_7)) {
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

    for (int i = 0; i < typeParameters.length; i++) {
      PsiTypeParameter typeParameter = typeParameters[i];
      PsiType substitution = substitutions[i];
      if (substitution != null) continue;

      Pair<PsiType, ConstraintType> constraint = constraints[i];
      if (constraint == null) {
        constraint = inferMethodTypeParameterFromParent(typeParameter, partialSubstitutor, parent, policy);
      }
      else if (constraint.getSecond() == ConstraintType.SUBTYPE) {
        Pair<PsiType, ConstraintType> otherConstraint =
          inferMethodTypeParameterFromParent(typeParameter, partialSubstitutor, parent, policy);
        if (otherConstraint != null) {
          if (otherConstraint.getSecond() == ConstraintType.EQUALS || otherConstraint.getSecond() == ConstraintType.SUPERTYPE || 
              compareSubtypes(constraint.getFirst(), otherConstraint.getFirst())) {
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
    return partialSubstitutor;
  }

  @NotNull
  @Override
  public PsiSubstitutor inferTypeArguments(@NotNull PsiTypeParameter[] typeParameters,
                                           @NotNull PsiType[] leftTypes,
                                           @NotNull PsiType[] rightTypes,
                                           @NotNull LanguageLevel languageLevel) {
    return inferTypeArguments(typeParameters, leftTypes, rightTypes, PsiSubstitutor.EMPTY, languageLevel);
  }

  private static boolean compareSubtypes(final PsiType type, final PsiType parentType) {
    return type != null && parentType != null && TypeConversionUtil.isAssignable(type, parentType);
  }

  @Override
  @NotNull
  public PsiSubstitutor inferTypeArguments(@NotNull PsiTypeParameter[] typeParameters,
                                           @NotNull PsiType[] leftTypes,
                                           @NotNull PsiType[] rightTypes,
                                           @NotNull PsiSubstitutor partialSubstitutor,
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
      return Pair.create(arg, constraintType);
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

    if (arg instanceof PsiCapturedWildcardType) {
      arg = ((PsiCapturedWildcardType)arg).getUpperBound();
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
        return Pair.create(bound, ConstraintType.SUPERTYPE);
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

  private static PsiType intersectAllExtends(PsiTypeParameter typeParam, PsiType arg) {
    if (arg == null) return null;
    PsiClassType[] superTypes = typeParam.getSuperTypes();
    PsiType[] erasureTypes = PsiType.createArray(superTypes.length);
    for (int i = 0; i < superTypes.length; i++) {
      erasureTypes[i] = TypeConversionUtil.erasure(superTypes[i]);
    }
    PsiType[] types = ArrayUtil.append(erasureTypes, arg, PsiType.class);
    assert types.length != 0;
    return PsiIntersectionType.createIntersection(types);
  }

  //represents the result of failed type inference: in case we failed inferring from parameters, do not perform inference from context
  private static final Pair<PsiType, ConstraintType> FAILED_INFERENCE = new Pair<>(PsiType.NULL, ConstraintType.EQUALS);

  @Nullable
  private Pair<PsiType, ConstraintType> getSubstitutionForTypeParameterInner(PsiType param,
                                                                                    PsiType arg,
                                                                                    PsiType patternType,
                                                                                    final ConstraintType constraintType,
                                                                                    final int depth) {
    if (patternType.equals(param)) {
      return processArgType(arg, constraintType, depth < 2);
    }

    if (arg instanceof PsiCapturedWildcardType && (depth < 2 || 
                                                   constraintType != ConstraintType.EQUALS || 
                                                   param instanceof PsiWildcardType)) {
      arg = ((PsiCapturedWildcardType)arg).getWildcard(); //reopen
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
      if (!paramClass.isEquivalentTo(argClass)) {
        return null;
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

      if (lowerBound != PsiType.NULL) return Pair.create(lowerBound, ConstraintType.SUPERTYPE);
      if (upperBound != PsiType.NULL) return Pair.create(upperBound, ConstraintType.SUBTYPE);

      return wildcardCaptured;
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
      if (parent instanceof PsiExpressionList) {
        final PsiElement pParent = parent.getParent();
        if (pParent instanceof PsiCallExpression && parent.equals(((PsiCallExpression)pParent).getArgumentList())) {
          constraint = policy.inferTypeConstraintFromCallContext(methodCall, (PsiExpressionList)parent, (PsiCallExpression)pParent, typeParameter);
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
            guess instanceof PsiIntersectionType &&
            !JavaVersionService.getInstance().isAtLeast(parent, JavaSdkVersion.JDK_1_7)) {
          for (PsiType conjuct : ((PsiIntersectionType)guess).getConjuncts()) {
            if (!conjuct.isAssignableFrom(expectedType)) {
              return FAILED_INFERENCE;
            }
          }
        }
      }
    }

    PsiType[] superTypes = typeParameter.getSuperTypes();
    final PsiType[] types = PsiType.createArray(superTypes.length);
    for (int i = 0; i < superTypes.length; i++) {
      PsiType superType = substitutor.substitute(superTypes[i]);
      if (superType instanceof PsiClassType && ((PsiClassType)superType).isRaw()) {
        superType = TypeConversionUtil.erasure(superType);
      }
      if (superType == null) superType = PsiType.getJavaLangObject(myManager, scope);
      types[i] = superType;
    }

    if (constraint == null) {
      if (methodCall instanceof PsiCallExpression) {
        if (types.length == 0) return null;
        return policy.getInferredTypeWithNoConstraint(myManager, PsiIntersectionType.createIntersection(types));
      }
      return null;
    }
    PsiType guess = constraint.getFirst();
    if (guess != null && types.length > 0) {
      guess = GenericsUtil.getGreatestLowerBound(guess, PsiIntersectionType.createIntersection(types));
    }
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

    return Pair.create(guess, constraint.getSecond());
  }
}

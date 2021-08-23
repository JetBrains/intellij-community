// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.PsiMethodReferenceCompatibilityConstraint;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.ClassCandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.conflictResolvers.JavaMethodsConflictResolver;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MethodReferenceResolver implements ResolveCache.PolyVariantContextResolver<PsiMethodReferenceExpressionImpl> {
  @Override
  public JavaResolveResult @NotNull [] resolve(@NotNull PsiMethodReferenceExpressionImpl reference, @NotNull PsiFile containingFile, boolean incompleteCode) {
    PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(reference);

    PsiClass containingClass = qualifierResolveResult.getContainingClass();
    PsiSubstitutor substitutor = qualifierResolveResult.getSubstitutor();

    if (containingClass != null) {
      PsiElement element = reference.getReferenceNameElement();
      boolean isConstructor = reference.isConstructor();
      if (element instanceof PsiIdentifier || isConstructor) {
        if (isConstructor && !canBeConstructed(containingClass)) {
          return JavaResolveResult.EMPTY_ARRAY;
        }
        PsiType functionalInterfaceType = reference.getFunctionalInterfaceType();
        PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
        PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
        PsiSubstitutor functionalInterfaceSubstitutor = interfaceMethod != null ? LambdaUtil.getSubstitutor(interfaceMethod, resolveResult) : null;
        MethodSignature signature = interfaceMethod != null ? interfaceMethod.getSignature(functionalInterfaceSubstitutor) : null;
        PsiType interfaceMethodReturnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
        if (isConstructor && containingClass.getConstructors().length == 0) {
          if (interfaceMethodReturnType != null) {
            PsiClassType returnType = composeReturnType(containingClass, substitutor);
            InferenceSession session = new InferenceSession(containingClass.getTypeParameters(), substitutor, reference.getManager(), null);
            if (!(session.isProperType(session.substituteWithInferenceVariables(returnType)) && session.isProperType(interfaceMethodReturnType))) {
              session.registerReturnTypeConstraints(returnType, interfaceMethodReturnType, reference);
              substitutor = session.infer();
            }
          }
          ClassCandidateInfo candidateInfo = null;
          boolean isArray = PsiUtil.isArrayClass(containingClass);
          if (signature == null ||
              !isArray && (containingClass.getContainingClass() == null || !isLocatedInStaticContext(containingClass, reference)) && signature.getParameterTypes().length == 0 ||
              isArray && arrayCreationSignature(signature)) {
            candidateInfo = new ClassCandidateInfo(containingClass, substitutor);
          }
          return candidateInfo == null ? JavaResolveResult.EMPTY_ARRAY : new JavaResolveResult[]{candidateInfo};
        }

        PsiConflictResolver conflictResolver = createResolver(reference, qualifierResolveResult, interfaceMethod, signature);
        MethodCandidatesProcessor processor =
          new MethodCandidatesProcessor(reference, containingFile, new PsiConflictResolver[] {conflictResolver}, new SmartList<>()) {
            @Override
            protected boolean acceptVarargs() {
              return true;
            }

            @Override
            protected @NotNull MethodCandidateInfo createCandidateInfo(@NotNull PsiMethod method,
                                                                       @NotNull PsiSubstitutor substitutor,
                                                                       boolean staticProblem,
                                                                       boolean accessible,
                                                                       boolean varargs) {
              PsiExpressionList argumentList = getArgumentList();
              PsiType[] typeParameters = reference.getTypeParameters();
              return new MethodCandidateInfo(method, substitutor, !accessible, staticProblem, argumentList, myCurrentFileContext,
                                             argumentList != null ? argumentList.getExpressionTypes() : null,
                                             method.hasTypeParameters() && typeParameters.length > 0 ? typeParameters : null,
                                             getLanguageLevel()) {
                @Override
                public boolean isVarargs() {
                  return varargs;
                }

                @NotNull
                @Override
                public PsiSubstitutor inferTypeArguments(@NotNull ParameterTypeInferencePolicy policy, boolean includeReturnConstraint) {
                  return includeReturnConstraint ? inferTypeArguments(true)
                                                 : Objects.requireNonNull(MethodCandidateInfo.ourOverloadGuard
                                                                            .doPreventingRecursion(reference, false,
                                                                                                   () -> inferTypeArguments(false)));
                }

                private PsiSubstitutor inferTypeArguments(boolean includeReturnConstraint) {
                  if (interfaceMethod == null) return substitutor;
                  InferenceSession session = new InferenceSession(method.getTypeParameters(), substitutor, reference.getManager(), reference);
                  session.initThrowsConstraints(method);
                  PsiSubstitutor psiSubstitutor = session.collectApplicabilityConstraints(reference, this, functionalInterfaceType);
                  if (psiSubstitutor != null) {
                    return psiSubstitutor;
                  }

                  if (!session.repeatInferencePhases()) {
                    List<String> errorMessages = session.getIncompatibleErrorMessages();
                    if (errorMessages != null) {
                      setApplicabilityError(StringUtil.join(errorMessages, "\n"));
                    }
                    return substitutor;
                  }

                  if (includeReturnConstraint && !PsiType.VOID.equals(interfaceMethodReturnType) && interfaceMethodReturnType != null) {
                    PsiType returnType = method.isConstructor()
                                               ? composeReturnType(containingClass, substitutor)
                                               : PsiMethodReferenceCompatibilityConstraint
                                                 .getSubstitutor(signature, qualifierResolveResult, method, containingClass, reference)
                                                 .substitute(method.getReturnType());
                    if (returnType != null) {
                      session.registerReturnTypeConstraints(returnType, interfaceMethodReturnType, reference);
                    }
                  }
                  return session.infer(method.getParameterList().getParameters(), null, null, null);
                }

                @Override
                public boolean isApplicable() {
                  if (signature == null) return false;
                  if (getInferenceErrorMessageAssumeAlreadyComputed() != null) return false;
                  PsiType[] argTypes = signature.getParameterTypes();
                  boolean hasReceiver = PsiMethodReferenceUtil.isSecondSearchPossible(argTypes, qualifierResolveResult, reference);

                  return MethodReferenceConflictResolver.isApplicableByFirstSearch(this, argTypes, hasReceiver, reference, interfaceMethod.isVarArgs(), interfaceMethod) != null;
                }
              };
            }
        };
        processor.setIsConstructor(isConstructor);
        processor.setName(isConstructor ? containingClass.getName() : element.getText());
        PsiExpression expression = reference.getQualifierExpression();
        if (expression == null || !(expression.getType() instanceof PsiArrayType) && !(expression instanceof PsiSuperExpression)) {
          processor.setAccessClass(containingClass);
        }

        if (qualifierResolveResult.isReferenceTypeQualified() && isLocatedInStaticContext(containingClass, reference)) {
           processor.handleEvent(JavaScopeProcessorEvent.START_STATIC, null);
        }
        ResolveState state = ResolveState.initial().put(PsiSubstitutor.KEY, substitutor);
        containingClass.processDeclarations(processor, state, reference, reference);
        return processor.getResult();
      }
    }
    return JavaResolveResult.EMPTY_ARRAY;
  }

  public static boolean canBeConstructed(@NotNull PsiClass psiClass) {
    return !psiClass.isEnum() && !psiClass.hasModifierProperty(PsiModifier.ABSTRACT) && !(psiClass instanceof PsiTypeParameter);
  }

  private static boolean isLocatedInStaticContext(@NotNull PsiClass containingClass, @NotNull PsiMethodReferenceExpression reference) {
    PsiClass gContainingClass = containingClass.getContainingClass();
    if (gContainingClass == null || !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass aClass = null;
      if (PsiTreeUtil.isAncestor(gContainingClass != null ? gContainingClass : containingClass, reference, false)) {
        aClass = gContainingClass != null ? gContainingClass : containingClass;
      }
      return PsiUtil.getEnclosingStaticElement(reference, aClass) != null;
    }
    return false;
  }

  protected PsiConflictResolver createResolver(@NotNull PsiMethodReferenceExpressionImpl referenceExpression,
                                               @NotNull PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult,
                                               @Nullable PsiMethod interfaceMethod,
                                               @Nullable MethodSignature signature) {
    return new MethodReferenceConflictResolver(referenceExpression, qualifierResolveResult, signature, interfaceMethod);
  }

  private static @NotNull PsiClassType composeReturnType(@NotNull PsiClass containingClass, @NotNull PsiSubstitutor substitutor) {
    boolean isRawSubst = PsiUtil.isRawSubstitutor(containingClass, substitutor);
    return JavaPsiFacade.getElementFactory(containingClass.getProject()).createType(containingClass, isRawSubst ? PsiSubstitutor.EMPTY : substitutor);
  }

  private static final class MethodReferenceConflictResolver extends JavaMethodsConflictResolver {
    private final MethodSignature mySignature;
    private final PsiMethod myInterfaceMethod;
    private final PsiMethodReferenceExpressionImpl myReferenceExpression;
    private final PsiMethodReferenceUtil.QualifierResolveResult myQualifierResolveResult;
    private final boolean myFunctionalMethodVarArgs;

    private MethodReferenceConflictResolver(@NotNull PsiMethodReferenceExpressionImpl referenceExpression,
                                            @NotNull PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult,
                                            @Nullable MethodSignature signature,
                                            @Nullable PsiMethod interfaceMethod) {
      super(referenceExpression, signature != null ? signature.getParameterTypes() : PsiType.EMPTY_ARRAY, PsiUtil.getLanguageLevel(referenceExpression), referenceExpression.getContainingFile());
      myReferenceExpression = referenceExpression;
      myQualifierResolveResult = qualifierResolveResult;
      myFunctionalMethodVarArgs =  interfaceMethod != null && interfaceMethod.isVarArgs();
      mySignature = signature;
      myInterfaceMethod = interfaceMethod;
    }

    @Override
    protected int getPertinentApplicabilityLevel(@NotNull MethodCandidateInfo conflict,
                                                 Map<MethodCandidateInfo, PsiSubstitutor> map) {
      return conflict.isVarargs() ? MethodCandidateInfo.ApplicabilityLevel.VARARGS : MethodCandidateInfo.ApplicabilityLevel.FIXED_ARITY;
    }

    @Nullable
    @Override
    protected CandidateInfo guardedOverloadResolution(@NotNull List<CandidateInfo> conflicts) {
      if (mySignature == null) return null;

      if (conflicts.isEmpty()) return null;
      if (conflicts.size() == 1) return conflicts.get(0);
      final Map<MethodCandidateInfo, PsiSubstitutor> map = FactoryMap.create(key -> key.getSubstitutor(false));

      checkSameSignatures(conflicts, map);
      if (conflicts.size() == 1) return  conflicts.get(0);

      checkAccessStaticLevels(conflicts, true);
      if (conflicts.size() == 1) return  conflicts.get(0);

      PsiType[] argTypes = mySignature.getParameterTypes();
      boolean hasReceiver = PsiMethodReferenceUtil.isSecondSearchPossible(argTypes, myQualifierResolveResult, myReferenceExpression);

      List<CandidateInfo> firstCandidates = new ArrayList<>();
      List<CandidateInfo> secondCandidates = new ArrayList<>();
      boolean thereIsStaticInTheFirst = false;
      boolean thereIsNonStaticInTheSecond = false;

      for (CandidateInfo conflict : conflicts) {
        if (!(conflict instanceof MethodCandidateInfo)) continue;
        Boolean applicableByFirstSearch = isApplicableByFirstSearch(conflict, argTypes, hasReceiver, myReferenceExpression, myFunctionalMethodVarArgs, myInterfaceMethod);
        if (applicableByFirstSearch != null) {
          (applicableByFirstSearch ? firstCandidates : secondCandidates).add(conflict);
          boolean isStatic = isStaticMethod(conflict);
          if (isStatic && applicableByFirstSearch) {
            thereIsStaticInTheFirst = true;
          }
          if (!isStatic && !applicableByFirstSearch) {
            thereIsNonStaticInTheSecond = true;
          }
        }
      }

      if (myQualifierResolveResult.isReferenceTypeQualified() && myReferenceExpression.getReferenceNameElement() instanceof PsiIdentifier) {
        //If the first search produces a static method, and no non-static method is applicable for the second search, then the result of the first search is the compile-time declaration.
        CandidateInfo candidateInfo = filterStaticCorrectCandidates(firstCandidates, secondCandidates, true);
        if (candidateInfo != null) {
          return candidateInfo;
        }

        //If the second search produces a non-static method, and no static method is applicable for the first search, then the result of the second search is the compile-time declaration.
        candidateInfo = filterStaticCorrectCandidates(secondCandidates, firstCandidates, false);
        if (candidateInfo != null) {
          return candidateInfo;
        }
      }

      CandidateInfo candidateInfo = resolveConflicts(firstCandidates, secondCandidates, map, MethodCandidateInfo.ApplicabilityLevel.FIXED_ARITY);
      candidateInfo = checkStaticNonStaticConflict(candidateInfo, firstCandidates, secondCandidates, thereIsStaticInTheFirst, thereIsNonStaticInTheSecond);
      if (candidateInfo != null) {
        return candidateInfo;
      }

      candidateInfo = resolveConflicts(firstCandidates, secondCandidates, map, MethodCandidateInfo.ApplicabilityLevel.VARARGS);
      candidateInfo = checkStaticNonStaticConflict(candidateInfo, firstCandidates, secondCandidates, thereIsStaticInTheFirst, thereIsNonStaticInTheSecond);
      if (candidateInfo != null) {
        return candidateInfo;
      }

      if (firstCandidates.isEmpty() && secondCandidates.isEmpty()) {
        return null;
      }

      conflicts.clear();
      firstCandidates.addAll(secondCandidates);
      conflicts.addAll(firstCandidates);
      return null;
    }

    private CandidateInfo checkStaticNonStaticConflict(CandidateInfo candidateInfo,
                                                       @NotNull List<CandidateInfo> firstCandidates,
                                                       @NotNull List<CandidateInfo> secondCandidates,
                                                       boolean thereIsStaticInTheFirst,
                                                       boolean thereIsNonStaticInTheSecond) {
      if (candidateInfo == null ||
          !myQualifierResolveResult.isReferenceTypeQualified() ||
          !(myReferenceExpression.getReferenceNameElement() instanceof PsiIdentifier)) {
        return candidateInfo;
      }
      boolean isStatic = isStaticMethod(candidateInfo);
      if (isStatic && !thereIsNonStaticInTheSecond && firstCandidates.contains(candidateInfo) ||
          !isStatic && !thereIsStaticInTheFirst && secondCandidates.contains(candidateInfo)) {
        return candidateInfo;
      }
      return null;
    }

    private static boolean isStaticMethod(@NotNull CandidateInfo candidateInfo) {
      return ((MethodCandidateInfo) candidateInfo).getElement().hasModifierProperty(PsiModifier.STATIC);
    }

    private static Boolean isApplicableByFirstSearch(@NotNull CandidateInfo conflict,
                                                     PsiType @NotNull [] functionalInterfaceParamTypes,
                                                     boolean hasReceiver,
                                                     @NotNull PsiMethodReferenceExpression referenceExpression,
                                                     boolean functionalMethodVarArgs,
                                                     PsiMethod interfaceMethod) {

      PsiMethod psiMethod = ((MethodCandidateInfo)conflict).getElement();

      PsiSubstitutor substitutor = ((MethodCandidateInfo)conflict).getSubstitutor(false);
      if (((MethodCandidateInfo)conflict).getInferenceErrorMessage() != null) return null;
      PsiType[] parameterTypes = psiMethod.getSignature(substitutor).getParameterTypes();

      boolean varargs = ((MethodCandidateInfo)conflict).isVarargs();
      if (varargs && (!psiMethod.isVarArgs() || functionalMethodVarArgs)) {
        return null;
      }

      if ((varargs || functionalInterfaceParamTypes.length == parameterTypes.length) &&
          isCorrectAssignment(parameterTypes, functionalInterfaceParamTypes, interfaceMethod, varargs, conflict, 0)) {
        //reject static interface methods called on something else but interface class
        if (psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
          PsiClass containingClass = psiMethod.getContainingClass();
          if (containingClass != null && containingClass.isInterface()) {
            PsiClass qualifierClass = PsiMethodReferenceUtil.getQualifierResolveResult(referenceExpression).getContainingClass();
            if (!containingClass.getManager().areElementsEquivalent(qualifierClass, containingClass)) {
              return null;
            }
          }
        } else if (hasReceiver && varargs &&
                   isCorrectAssignment(parameterTypes, functionalInterfaceParamTypes, interfaceMethod, true, conflict, 1)) {
          return false;
        }
        return true;
      }

      if (hasReceiver &&
          (varargs || functionalInterfaceParamTypes.length == parameterTypes.length + 1) &&
          isCorrectAssignment(parameterTypes, functionalInterfaceParamTypes, interfaceMethod, varargs, conflict, 1)) {
        return false;
      }
      return null;
    }

    private static boolean isCorrectAssignment(PsiType @NotNull [] parameterTypes,
                                               PsiType @NotNull [] functionalInterfaceParamTypes,
                                               PsiMethod interfaceMethod,
                                               boolean varargs,
                                               @NotNull CandidateInfo conflict,
                                               int offset) {
      int min = Math.min(parameterTypes.length, functionalInterfaceParamTypes.length - offset);
      for (int i = 0; i < min; i++) {
        PsiType argType = PsiUtil.captureToplevelWildcards(functionalInterfaceParamTypes[i + offset], interfaceMethod.getParameterList().getParameters()[i]);
        PsiType parameterType = parameterTypes[i];
        if (varargs && i == parameterTypes.length - 1) {
          if (!TypeConversionUtil.isAssignable(parameterType, argType) &&
              !TypeConversionUtil.isAssignable(((PsiArrayType)parameterType).getComponentType(), argType)) {
            markNotApplicable(conflict);
            return false;
          }
        }
        else if (!TypeConversionUtil.isAssignable(parameterType, argType)) {
          markNotApplicable(conflict);
          return false;
        }
      }
      return !varargs || parameterTypes.length - 1 <= functionalInterfaceParamTypes.length - offset;
    }

    private static void markNotApplicable(@NotNull CandidateInfo conflict) {
      if (conflict instanceof MethodCandidateInfo) {
        ((MethodCandidateInfo)conflict).markNotApplicable();
      }
    }

    private CandidateInfo resolveConflicts(@NotNull List<CandidateInfo> firstCandidates,
                                           @NotNull List<CandidateInfo> secondCandidates,
                                           Map<MethodCandidateInfo, PsiSubstitutor> map,
                                           int applicabilityLevel) {
      int firstApplicability = checkApplicability(firstCandidates);
      checkSpecifics(firstCandidates, applicabilityLevel, map, 0);

      int secondApplicability = checkApplicability(secondCandidates);
      checkSpecifics(secondCandidates, applicabilityLevel, map, 1);

      if (firstApplicability < secondApplicability) {
        return secondCandidates.size() == 1 ? secondCandidates.get(0) : null;
      }

      if (secondApplicability < firstApplicability) {
        return firstCandidates.size() == 1 ? firstCandidates.get(0) : null;
      }

      return firstCandidates.size() + secondCandidates.size() == 1
             ? firstCandidates.isEmpty()
               ? secondCandidates.get(0)
               : firstCandidates.get(0)
             : null;
    }

    @Override
    protected boolean nonComparable(@NotNull CandidateInfo method, @NotNull CandidateInfo conflict, boolean fixedArity) {
      if (method == conflict) return true;
      PsiElement psiElement = method.getElement();
      PsiElement conflictElement = conflict.getElement();
      if (psiElement instanceof PsiMethod && conflictElement instanceof PsiMethod) {
        return fixedArity &&
               ((PsiMethod)psiElement).getParameterList().getParametersCount() !=
               ((PsiMethod)conflictElement).getParameterList().getParametersCount();
      }
      return false;
    }

    /**
     * 15.13.1
     */
    private static CandidateInfo filterStaticCorrectCandidates(@NotNull List<CandidateInfo> firstCandidates,
                                                               @NotNull List<CandidateInfo> secondCandidates,
                                                               boolean shouldBeStatic) {
      if (firstCandidates.size() == 1) {
        CandidateInfo candidateInfo = firstCandidates.get(0);
        PsiElement element = candidateInfo.getElement();
        if (element instanceof PsiMethod) {
          boolean isStatic = ((PsiMethod)element).hasModifierProperty(PsiModifier.STATIC);
          if (shouldBeStatic == isStatic) {
            for (CandidateInfo secondCandidate : secondCandidates) {
              PsiElement psiElement = secondCandidate.getElement();
              if (psiElement instanceof PsiMethod) {
                boolean oppositeStatic = ((PsiMethod)psiElement).hasModifierProperty(PsiModifier.STATIC);
                if (shouldBeStatic != oppositeStatic) {
                  return null;
                }
              }
            }
            return candidateInfo;
          }
        }
      }
      return null;
    }
  }

  private static boolean arrayCreationSignature(@NotNull MethodSignature signature) {
    PsiType[] parameterTypes = signature.getParameterTypes();
    if (parameterTypes.length == 1 && parameterTypes[0] != null) {
      if (TypeConversionUtil.isAssignable(PsiType.INT, parameterTypes[0])) {
        return true;
      }
      if (parameterTypes[0] instanceof PsiClassType) {
        return ((PsiClassType)parameterTypes[0]).resolve() instanceof PsiTypeParameter;
      }
    }
    return false;
  }
}

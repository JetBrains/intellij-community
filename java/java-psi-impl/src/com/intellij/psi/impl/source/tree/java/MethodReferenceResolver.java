/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.diagnostic.Logger;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MethodReferenceResolver implements ResolveCache.PolyVariantContextResolver<PsiMethodReferenceExpressionImpl> {
  private static final Logger LOG = Logger.getInstance(MethodReferenceResolver.class);

  @NotNull
  @Override
  public JavaResolveResult[] resolve(@NotNull final PsiMethodReferenceExpressionImpl reference, @NotNull final PsiFile containingFile, boolean incompleteCode) {
    final PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult = PsiMethodReferenceUtil.getQualifierResolveResult(reference);

    final PsiClass containingClass = qualifierResolveResult.getContainingClass();
    PsiSubstitutor substitutor = qualifierResolveResult.getSubstitutor();

    if (containingClass != null) {
      final PsiElement element = reference.getReferenceNameElement();
      final boolean isConstructor = reference.isConstructor();
      if (element instanceof PsiIdentifier || isConstructor) {
        if (isConstructor && !canBeConstructed(containingClass)) {
          return JavaResolveResult.EMPTY_ARRAY;
        }
        final PsiType functionalInterfaceType = getInterfaceType(reference);
        final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
        final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
        final PsiSubstitutor functionalInterfaceSubstitutor = interfaceMethod != null ? LambdaUtil.getSubstitutor(interfaceMethod, resolveResult) : null;
        final MethodSignature signature = interfaceMethod != null ? interfaceMethod.getSignature(functionalInterfaceSubstitutor) : null;
        final PsiType interfaceMethodReturnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);
        if (isConstructor && containingClass.getConstructors().length == 0) {
          if (interfaceMethod != null) {
            final PsiClassType returnType = composeReturnType(containingClass, substitutor);
            final InferenceSession session = new InferenceSession(containingClass.getTypeParameters(), substitutor, reference.getManager(), null);
            if (!(session.isProperType(session.substituteWithInferenceVariables(returnType)) && session.isProperType(interfaceMethodReturnType))) {
              session.registerReturnTypeConstraints(returnType, interfaceMethodReturnType, reference);
              substitutor = session.infer();
            }
          }
          ClassCandidateInfo candidateInfo = null;
          final boolean isArray = PsiEquivalenceUtil.areElementsEquivalent(containingClass, JavaPsiFacade.getElementFactory(reference.getProject()).getArrayClass(PsiUtil.getLanguageLevel(reference)));
          if (signature == null ||
              !isArray && (containingClass.getContainingClass() == null || !isLocatedInStaticContext(containingClass, reference)) && signature.getParameterTypes().length == 0 ||
              isArray && arrayCreationSignature(signature)) {
            candidateInfo = new ClassCandidateInfo(containingClass, substitutor);
          }
          return candidateInfo == null ? JavaResolveResult.EMPTY_ARRAY : new JavaResolveResult[]{candidateInfo};
        }

        final PsiConflictResolver conflictResolver = createResolver(reference, qualifierResolveResult, interfaceMethod, signature);
        final MethodCandidatesProcessor processor =
          new MethodCandidatesProcessor(reference, containingFile, new PsiConflictResolver[] {conflictResolver}, new SmartList<>()) {
            @Override
            protected boolean acceptVarargs() {
              return true;
            }

            @Override
            protected MethodCandidateInfo createCandidateInfo(@NotNull final PsiMethod method,
                                                              @NotNull final PsiSubstitutor substitutor,
                                                              final boolean staticProblem,
                                                              final boolean accessible,
                                                              final boolean varargs) {
              final PsiExpressionList argumentList = getArgumentList();
              final PsiType[] typeParameters = reference.getTypeParameters();
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
                  return inferTypeArguments(includeReturnConstraint);
                }

                private PsiSubstitutor inferTypeArguments(boolean includeReturnConstraint) {
                  if (interfaceMethod == null) return substitutor;
                  final InferenceSession session = new InferenceSession(method.getTypeParameters(), substitutor, reference.getManager(), reference);
                  session.initThrowsConstraints(method);
                  final PsiSubstitutor psiSubstitutor = session.collectApplicabilityConstraints(reference, this, functionalInterfaceType);
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
                    PsiSubstitutor subst = PsiMethodReferenceCompatibilityConstraint.getSubstitutor(signature, qualifierResolveResult, method, containingClass, reference);
                    final PsiType returnType = method.isConstructor() ? composeReturnType(containingClass, subst) : subst.substitute(method.getReturnType());
                    if (returnType != null) {
                      session.registerReturnTypeConstraints(returnType, interfaceMethodReturnType, reference);
                    }
                  }
                  return session.infer(method.getParameterList().getParameters(), null, null);
                }

                @Override
                public boolean isApplicable() {
                  if (signature == null) return false;
                  if (getInferenceErrorMessageAssumeAlreadyComputed() != null) return false;
                  final PsiType[] argTypes = signature.getParameterTypes();
                  boolean hasReceiver = PsiMethodReferenceUtil.isSecondSearchPossible(argTypes, qualifierResolveResult, reference);

                  return MethodReferenceConflictResolver.isApplicableByFirstSearch(this, argTypes, hasReceiver, reference, interfaceMethod.isVarArgs(), interfaceMethod) != null;
                }
              };
            }
        };
        processor.setIsConstructor(isConstructor);
        processor.setName(isConstructor ? containingClass.getName() : element.getText());
        final PsiExpression expression = reference.getQualifierExpression();
        if (expression == null || !(expression.getType() instanceof PsiArrayType)) {
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

  private static boolean isLocatedInStaticContext(PsiClass containingClass, PsiMethodReferenceExpression reference) {
    final PsiClass gContainingClass = containingClass.getContainingClass();
    if (gContainingClass == null || !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass aClass = null;
      if (PsiTreeUtil.isAncestor(gContainingClass != null ? gContainingClass : containingClass, reference, false)) {
        aClass = gContainingClass != null ? gContainingClass : containingClass;
      }
      if (PsiUtil.getEnclosingStaticElement(reference, aClass) != null) {
        return true;
      }
    }
    return false;
  }

  protected PsiType getInterfaceType(PsiMethodReferenceExpression reference) {
    return reference.getFunctionalInterfaceType();
  }

  protected PsiConflictResolver createResolver(PsiMethodReferenceExpressionImpl referenceExpression,
                                               PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult,
                                               PsiMethod interfaceMethod,
                                               MethodSignature signature) {
    return new MethodReferenceConflictResolver(referenceExpression, qualifierResolveResult, signature, interfaceMethod);
  }

  private static PsiClassType composeReturnType(PsiClass containingClass, PsiSubstitutor substitutor) {
    final boolean isRawSubst = PsiUtil.isRawSubstitutor(containingClass, substitutor);
    return JavaPsiFacade.getElementFactory(containingClass.getProject()).createType(containingClass, isRawSubst ? PsiSubstitutor.EMPTY : substitutor);
  }

  private static class MethodReferenceConflictResolver extends JavaMethodsConflictResolver {
    private final MethodSignature mySignature;
    private final PsiMethod myInterfaceMethod;
    private final PsiMethodReferenceExpressionImpl myReferenceExpression;
    private final PsiMethodReferenceUtil.QualifierResolveResult myQualifierResolveResult;
    private final boolean myFunctionalMethodVarArgs;

    private MethodReferenceConflictResolver(PsiMethodReferenceExpressionImpl referenceExpression,
                                            PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult,
                                            @Nullable MethodSignature signature, PsiMethod interfaceMethod) {
      super(referenceExpression, signature != null ? signature.getParameterTypes() : PsiType.EMPTY_ARRAY, PsiUtil.getLanguageLevel(referenceExpression));
      myReferenceExpression = referenceExpression;
      myQualifierResolveResult = qualifierResolveResult;
      myFunctionalMethodVarArgs =  interfaceMethod != null && interfaceMethod.isVarArgs();
      mySignature = signature;
      myInterfaceMethod = interfaceMethod;
    }

    @Override
    protected int getPertinentApplicabilityLevel(@NotNull MethodCandidateInfo conflict) {
      return conflict.isVarargs() ? MethodCandidateInfo.ApplicabilityLevel.VARARGS : MethodCandidateInfo.ApplicabilityLevel.FIXED_ARITY;
    }

    @Nullable
    @Override
    protected CandidateInfo guardedOverloadResolution(@NotNull List<CandidateInfo> conflicts) {
      if (mySignature == null) return null;

      if (conflicts.isEmpty()) return null;
      if (conflicts.size() == 1) return conflicts.get(0);

      checkSameSignatures(conflicts);
      if (conflicts.size() == 1) return  conflicts.get(0);

      checkAccessStaticLevels(conflicts, true);
      if (conflicts.size() == 1) return  conflicts.get(0);

      final PsiType[] argTypes = mySignature.getParameterTypes();
      boolean hasReceiver = PsiMethodReferenceUtil.isSecondSearchPossible(argTypes, myQualifierResolveResult, myReferenceExpression);

      final List<CandidateInfo> firstCandidates = new ArrayList<>();
      final List<CandidateInfo> secondCandidates = new ArrayList<>();

      for (CandidateInfo conflict : conflicts) {
        if (!(conflict instanceof MethodCandidateInfo)) continue;
        final Boolean applicableByFirstSearch = isApplicableByFirstSearch(conflict, argTypes, hasReceiver, myReferenceExpression, myFunctionalMethodVarArgs, myInterfaceMethod);
        if (applicableByFirstSearch != null) {
          (applicableByFirstSearch ? firstCandidates : secondCandidates).add(conflict);
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

      CandidateInfo candidateInfo = resolveConflicts(firstCandidates, secondCandidates, MethodCandidateInfo.ApplicabilityLevel.FIXED_ARITY);
      if (candidateInfo != null) {
        return candidateInfo;
      }

      candidateInfo = resolveConflicts(firstCandidates, secondCandidates, MethodCandidateInfo.ApplicabilityLevel.VARARGS);
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

    private static Boolean isApplicableByFirstSearch(CandidateInfo conflict,
                                                     PsiType[] functionalInterfaceParamTypes,
                                                     boolean hasReceiver,
                                                     PsiMethodReferenceExpression referenceExpression,
                                                     boolean functionalMethodVarArgs,
                                                     PsiMethod interfaceMethod) {

      final PsiMethod psiMethod = ((MethodCandidateInfo)conflict).getElement();

      final PsiSubstitutor substitutor = ((MethodCandidateInfo)conflict).getSubstitutor(false);
      final PsiType[] parameterTypes = psiMethod.getSignature(substitutor).getParameterTypes();

      final boolean varargs = ((MethodCandidateInfo)conflict).isVarargs();
      if (varargs && (!psiMethod.isVarArgs() || functionalMethodVarArgs)) {
        return null;
      }

      if ((varargs || functionalInterfaceParamTypes.length == parameterTypes.length) &&
          isCorrectAssignment(parameterTypes, functionalInterfaceParamTypes, interfaceMethod, varargs, referenceExpression, conflict, 0)) {
        return true;
      }

      if (hasReceiver &&
          (varargs || functionalInterfaceParamTypes.length == parameterTypes.length + 1) &&
          isCorrectAssignment(parameterTypes, functionalInterfaceParamTypes, interfaceMethod, varargs, referenceExpression, conflict, 1)) {
        return false;
      }
      return null;
    }

    private static boolean isCorrectAssignment(PsiType[] parameterTypes,
                                               PsiType[] functionalInterfaceParamTypes,
                                               PsiMethod interfaceMethod,
                                               boolean varargs,
                                               PsiMethodReferenceExpression referenceExpression,
                                               CandidateInfo conflict,
                                               int offset) {
      final int min = Math.min(parameterTypes.length, functionalInterfaceParamTypes.length - offset);
      for (int i = 0; i < min; i++) {
        final PsiType argType = PsiUtil.captureToplevelWildcards(functionalInterfaceParamTypes[i + offset], interfaceMethod.getParameterList().getParameters()[i]);
        final PsiType parameterType = parameterTypes[i];
        if (varargs && i == parameterTypes.length - 1) {
          if (!TypeConversionUtil.isAssignable(parameterType, argType) &&
              !TypeConversionUtil.isAssignable(((PsiArrayType)parameterType).getComponentType(), argType)) {
            reportParameterConflict(referenceExpression, conflict, argType, parameterType);
            return false;
          }
        }
        else if (!TypeConversionUtil.isAssignable(parameterType, argType)) {
          reportParameterConflict(referenceExpression, conflict, argType, parameterType);
          return false;
        }
      }
      return !varargs || parameterTypes.length - 1 <= functionalInterfaceParamTypes.length - offset;
    }

    private static void reportParameterConflict(PsiMethodReferenceExpression referenceExpression,
                                                CandidateInfo conflict,
                                                PsiType argType, 
                                                PsiType parameterType) {
      if (conflict instanceof MethodCandidateInfo) {
        ((MethodCandidateInfo)conflict).setApplicabilityError("Invalid " +
                                                              (referenceExpression.isConstructor() ? "constructor" :"method") +
                                                              " reference: " + argType.getPresentableText() + " cannot be converted to " + parameterType.getPresentableText());
      }
    }

    private CandidateInfo resolveConflicts(List<CandidateInfo> firstCandidates, List<CandidateInfo> secondCandidates, int applicabilityLevel) {

      final int firstApplicability = checkApplicability(firstCandidates);
      checkSpecifics(firstCandidates, applicabilityLevel, myLanguageLevel);

      final int secondApplicability = checkApplicability(secondCandidates);
      checkSpecifics(secondCandidates, applicabilityLevel, myLanguageLevel);
      
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
        if (fixedArity && ((PsiMethod)psiElement).getParameterList().getParametersCount() != ((PsiMethod)conflictElement).getParameterList().getParametersCount()) {
          return true;
        }
      }
      return false;
    }

    /**
     * 15.13.1
     */
    private static CandidateInfo filterStaticCorrectCandidates(List<CandidateInfo> firstCandidates,
                                                               List<CandidateInfo> secondCandidates,
                                                               boolean shouldBeStatic) {
      if (firstCandidates.size() == 1) {
        final CandidateInfo candidateInfo = firstCandidates.get(0);
        final PsiElement element = candidateInfo.getElement();
        if (element instanceof PsiMethod) {
          final boolean isStatic = ((PsiMethod)element).hasModifierProperty(PsiModifier.STATIC);
          if (shouldBeStatic && isStatic || !shouldBeStatic && !isStatic) {
            for (CandidateInfo secondCandidate : secondCandidates) {
              final PsiElement psiElement = secondCandidate.getElement();
              if (psiElement instanceof PsiMethod) {
                final boolean oppositeStatic = ((PsiMethod)psiElement).hasModifierProperty(PsiModifier.STATIC);
                if (shouldBeStatic && !oppositeStatic || !shouldBeStatic && oppositeStatic) {
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

  private static boolean arrayCreationSignature(MethodSignature signature) {
    final PsiType[] parameterTypes = signature.getParameterTypes();
    if (parameterTypes.length == 1 && parameterTypes[0] != null && TypeConversionUtil.isAssignable(PsiType.INT, parameterTypes[0])) {
      return true;
    }
    return false;
  }
}

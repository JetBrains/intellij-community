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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.constraints.TypeCompatibilityConstraint;
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
import java.util.Map;

class MethodReferenceResolver implements ResolveCache.PolyVariantContextResolver<PsiMethodReferenceExpressionImpl> {
  private static final Logger LOG = Logger.getInstance("#" + MethodReferenceResolver.class.getName());

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
        if (isConstructor && (containingClass.isEnum() || containingClass.hasModifierProperty(PsiModifier.ABSTRACT))) {
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
            if (!(session.isProperType(returnType) && session.isProperType(interfaceMethodReturnType))) {
              session.registerConstraints(returnType, interfaceMethodReturnType);
              substitutor = session.infer();
            }
          }
          ClassCandidateInfo candidateInfo = null;
          final boolean isArray = containingClass == JavaPsiFacade.getElementFactory(reference.getProject()).getArrayClass(PsiUtil.getLanguageLevel(containingClass));
          if (signature == null ||
              !isArray && (containingClass.getContainingClass() == null || !isLocatedInStaticContext(containingClass, reference)) && signature.getParameterTypes().length == 0 ||
              isArray && arrayCreationSignature(signature)) {
            candidateInfo = new ClassCandidateInfo(containingClass, substitutor);
          }
          return candidateInfo == null ? JavaResolveResult.EMPTY_ARRAY : new JavaResolveResult[]{candidateInfo};
        }

        final PsiConflictResolver conflictResolver = createResolver(reference, qualifierResolveResult, interfaceMethod, signature);
        final MethodCandidatesProcessor processor =
          new MethodCandidatesProcessor(reference, containingFile, new PsiConflictResolver[] {conflictResolver}, new SmartList<CandidateInfo>()) {
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
                                             argumentList != null ? argumentList.getExpressionTypes() : null, typeParameters.length > 0 ? typeParameters : null,
                                             getLanguageLevel()) {
                @Override
                public boolean isVarargs() {
                  return varargs;
                }

                @NotNull
                @Override
                public PsiSubstitutor inferTypeArguments(@NotNull ParameterTypeInferencePolicy policy, boolean includeReturnConstraint) {
                  return inferTypeArguments(varargs);
                }

                public PsiSubstitutor inferTypeArguments(boolean varargs) {
                  if (interfaceMethod == null) return substitutor;
                  final PsiSubstitutor qualifierResultSubstitutor = qualifierResolveResult.getSubstitutor();
                  final InferenceSession session = new InferenceSession(method.getTypeParameters(), substitutor, reference.getManager(), reference);

                  //lift parameters from outer call
                  final CurrentCandidateProperties methodSubstitutorPair = MethodCandidateInfo.getCurrentMethod(reference.getParent());
                  if (methodSubstitutorPair != null) {
                    session.initBounds(methodSubstitutorPair.getMethod().getTypeParameters());
                  }

                  final PsiParameter[] functionalMethodParameters = interfaceMethod.getParameterList().getParameters();
                  final PsiParameter[] parameters = method.getParameterList().getParameters();
                  final boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
                  if (parameters.length == functionalMethodParameters.length && !varargs || isStatic && varargs) {//static methods

                    if (method.isConstructor() && PsiUtil.isRawSubstitutor(containingClass, qualifierResultSubstitutor)) {
                      session.initBounds(containingClass.getTypeParameters());
                    }

                    for (int i = 0; i < functionalMethodParameters.length; i++) {
                      final PsiType pType = signature.getParameterTypes()[i];
                      session.addConstraint(new TypeCompatibilityConstraint(getParameterType(parameters, i, varargs), pType));
                    }
                  }
                  else if (parameters.length + 1 == functionalMethodParameters.length && !varargs || !isStatic && varargs && functionalMethodParameters.length > 0) { //instance methods
                    final PsiClass aClass = qualifierResolveResult.getContainingClass();
                    session.initBounds(aClass.getTypeParameters());

                    final PsiType pType = signature.getParameterTypes()[0];

                    PsiSubstitutor psiSubstitutor = qualifierResultSubstitutor;
                    // 15.28.1 If the ReferenceType is a raw type, and there exists a parameterization of this type, T, that is a supertype of P1,
                    // the type to search is the result of capture conversion (5.1.10) applied to T; 
                    // otherwise, the type to search is the same as the type of the first search. Again, the type arguments, if any, are given by the method reference.
                    if (PsiUtil.isRawSubstitutor(containingClass, qualifierResultSubstitutor)) {
                      final PsiClassType.ClassResolveResult pResult = PsiUtil.resolveGenericsClassInType(pType);
                      final PsiClass pClass = pResult.getElement();
                      final PsiSubstitutor receiverSubstitutor = pClass != null ? TypeConversionUtil
                        .getClassSubstitutor(containingClass, pClass, pResult.getSubstitutor()) : null;
                      if (receiverSubstitutor != null) {
                        if (!method.hasTypeParameters()) {
                          if (signature.getParameterTypes().length == 1 || PsiUtil.isRawSubstitutor(containingClass, receiverSubstitutor)) {
                            return receiverSubstitutor;
                          }
                        }
                        psiSubstitutor = receiverSubstitutor;
                      }
                    }

                    final PsiType qType = JavaPsiFacade.getElementFactory(reference.getProject()).createType(containingClass, psiSubstitutor);

                    session.addConstraint(new TypeCompatibilityConstraint(qType, pType));
                    
                    for (int i = 0; i < signature.getParameterTypes().length - 1; i++) {
                      final PsiType interfaceParamType = signature.getParameterTypes()[i + 1];
                      session.addConstraint(new TypeCompatibilityConstraint(getParameterType(parameters, i, varargs), interfaceParamType));
                    }
                  }

                  if (!session.repeatInferencePhases(false)) {
                    return substitutor;
                  }

                  if (interfaceMethodReturnType != PsiType.VOID && interfaceMethodReturnType != null) {
                    final PsiType returnType = method.isConstructor() ? composeReturnType(containingClass, substitutor) : method.getReturnType();
                    if (returnType != null) {
                      session.registerConstraints(returnType, interfaceMethodReturnType);
                    }
                  }
                  return session.infer(parameters, null, null);
                }

                private PsiType getParameterType(PsiParameter[] parameters, int i, boolean varargs) {
                  if (varargs && i >= parameters.length - 1) {
                    final PsiType type = parameters[parameters.length - 1].getType();
                    LOG.assertTrue(type instanceof PsiEllipsisType);
                    return ((PsiEllipsisType)type).getComponentType();
                  }
                  return parameters[i].getType();
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
    PsiType functionalInterfaceType = null;
    final Map<PsiMethodReferenceExpression,PsiType> map = PsiMethodReferenceUtil.ourRefs.get();
    if (map != null) {
      functionalInterfaceType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(map.get(reference));
    }

    if (functionalInterfaceType == null) {
      functionalInterfaceType = reference.getFunctionalInterfaceType();
    }

    return functionalInterfaceType;
  }

  protected PsiConflictResolver createResolver(PsiMethodReferenceExpressionImpl referenceExpression,
                                               PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult,
                                               PsiMethod interfaceMethod,
                                               MethodSignature signature) {
    return new MethodReferenceConflictResolver(referenceExpression, qualifierResolveResult, signature,
                                               interfaceMethod != null && interfaceMethod.isVarArgs());
  }

  private static PsiClassType composeReturnType(PsiClass containingClass, PsiSubstitutor substitutor) {
    final boolean isRawSubst = PsiUtil.isRawSubstitutor(containingClass, substitutor);
    return JavaPsiFacade.getElementFactory(containingClass.getProject())
      .createType(containingClass, isRawSubst ? PsiSubstitutor.EMPTY : substitutor);
  }

  private static class MethodReferenceConflictResolver extends JavaMethodsConflictResolver {
    private final MethodSignature mySignature;
    private final PsiMethodReferenceExpressionImpl myReferenceExpression;
    private final PsiMethodReferenceUtil.QualifierResolveResult myQualifierResolveResult;
    private final boolean myFunctionalMethodVarArgs;

    private MethodReferenceConflictResolver(PsiMethodReferenceExpressionImpl referenceExpression,
                                            PsiMethodReferenceUtil.QualifierResolveResult qualifierResolveResult,
                                            @Nullable MethodSignature signature, boolean varArgs) {
      super(referenceExpression, signature != null ? signature.getParameterTypes() : PsiType.EMPTY_ARRAY, PsiUtil.getLanguageLevel(referenceExpression));
      myReferenceExpression = referenceExpression;
      myQualifierResolveResult = qualifierResolveResult;
      myFunctionalMethodVarArgs = varArgs;
      mySignature = signature;
    }

    @Override
    protected int getPertinentApplicabilityLevel(MethodCandidateInfo conflict) {
      return conflict.isVarargs() ? MethodCandidateInfo.ApplicabilityLevel.VARARGS : MethodCandidateInfo.ApplicabilityLevel.FIXED_ARITY;
    }

    @Nullable
    @Override
    public CandidateInfo resolveConflict(@NotNull List<CandidateInfo> conflicts) {
      if (mySignature == null) return null;

      checkSameSignatures(conflicts);
      checkAccessStaticLevels(conflicts, true);

      final PsiType[] parameterTypes = mySignature.getParameterTypes();
      boolean hasReceiver = PsiMethodReferenceUtil.hasReceiver(parameterTypes, myQualifierResolveResult, myReferenceExpression);

      final List<CandidateInfo> firstCandidates = new ArrayList<CandidateInfo>();
      final List<CandidateInfo> secondCandidates = new ArrayList<CandidateInfo>();

      for (CandidateInfo conflict : conflicts) {
        if (!(conflict instanceof MethodCandidateInfo)) continue;
        final PsiMethod psiMethod = ((MethodCandidateInfo)conflict).getElement();

        final PsiSubstitutor substitutor = conflict.getSubstitutor();
        final PsiType[] signatureParameterTypes2 = psiMethod.getSignature(substitutor).getParameterTypes();

        final boolean varargs = ((MethodCandidateInfo)conflict).isVarargs();
        if (varargs && (!psiMethod.isVarArgs() || myFunctionalMethodVarArgs)) continue;

        if ((varargs || parameterTypes.length == signatureParameterTypes2.length) &&
            PsiMethodReferenceUtil.isCorrectAssignment(signatureParameterTypes2, parameterTypes, substitutor, varargs, 0)) {
          firstCandidates.add(conflict);
        }

        if (hasReceiver &&
            (varargs || parameterTypes.length == signatureParameterTypes2.length + 1) &&
            PsiMethodReferenceUtil.isCorrectAssignment(signatureParameterTypes2, parameterTypes, substitutor, varargs, 1)) {
          secondCandidates.add(conflict);
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

      if (resolveConflicts(firstCandidates, secondCandidates, MethodCandidateInfo.ApplicabilityLevel.FIXED_ARITY)) {
        return !firstCandidates.isEmpty() ? firstCandidates.get(0) : secondCandidates.get(0);
      }

      if (resolveConflicts(firstCandidates, secondCandidates, MethodCandidateInfo.ApplicabilityLevel.VARARGS)) {
        return !firstCandidates.isEmpty() ? firstCandidates.get(0) : secondCandidates.get(0);
      }

      conflicts.clear();
      firstCandidates.addAll(secondCandidates);
      conflicts.addAll(firstCandidates);
      return null;
    }

    private boolean resolveConflicts(List<CandidateInfo> firstCandidates, List<CandidateInfo> secondCandidates, int applicabilityLevel) {

      checkApplicability(firstCandidates);
      checkSpecifics(firstCandidates, applicabilityLevel, myLanguageLevel);

      checkApplicability(secondCandidates);
      checkSpecifics(secondCandidates, applicabilityLevel, myLanguageLevel);

      return firstCandidates.size() + secondCandidates.size() == 1;
    }

    @Override
    protected boolean nonComparable(CandidateInfo method, CandidateInfo conflict) {
      if (method == conflict) return true;
      PsiElement psiElement = method.getElement();
      PsiElement conflictElement = conflict.getElement();
      if (psiElement instanceof PsiMethod && conflictElement instanceof PsiMethod) {
        if (((PsiMethod)psiElement).getParameterList().getParametersCount() !=
            ((PsiMethod)conflictElement).getParameterList().getParametersCount()) {
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

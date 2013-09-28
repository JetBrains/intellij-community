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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.PsiGraphInferenceHelper;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.processor.MethodCandidatesProcessor;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiResolveHelperImpl implements PsiResolveHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.PsiResolveHelperImpl");
  private final PsiManager myManager;

  public PsiResolveHelperImpl(PsiManager manager) {
    myManager = manager;
  }

  @Override
  @NotNull
  public JavaResolveResult resolveConstructor(PsiClassType classType, PsiExpressionList argumentList, PsiElement place) {
    JavaResolveResult[] result = multiResolveConstructor(classType, argumentList, place);
    return result.length == 1 ? result[0] : JavaResolveResult.EMPTY;
  }

  @Override
  @NotNull
  public JavaResolveResult[] multiResolveConstructor(@NotNull PsiClassType type, @NotNull PsiExpressionList argumentList, @NotNull PsiElement place) {
    PsiClassType.ClassResolveResult classResolveResult = type.resolveGenerics();
    PsiClass aClass = classResolveResult.getElement();
    if (aClass == null) {
      return JavaResolveResult.EMPTY_ARRAY;
    }
    final MethodResolverProcessor processor;
    PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
    if (argumentList.getParent() instanceof PsiAnonymousClass) {
      final PsiAnonymousClass anonymous = (PsiAnonymousClass)argumentList.getParent();
      processor = new MethodResolverProcessor(anonymous, argumentList, place, place.getContainingFile());
      aClass = anonymous.getBaseClassType().resolve();
      if (aClass == null) return JavaResolveResult.EMPTY_ARRAY;
      substitutor = substitutor.putAll(TypeConversionUtil.getSuperClassSubstitutor(aClass, anonymous, substitutor));
    }
    else {
      processor = new MethodResolverProcessor(aClass, argumentList, place, place.getContainingFile());
    }

    ResolveState state = ResolveState.initial().put(PsiSubstitutor.KEY, substitutor);
    for (PsiMethod constructor : aClass.getConstructors()) {
      if (!processor.execute(constructor, state)) break;
    }

    return processor.getResult();
  }

  @Override
  public PsiClass resolveReferencedClass(@NotNull final String referenceText, final PsiElement context) {
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(myManager.getProject()).getParserFacade();
    try {
      final PsiJavaCodeReferenceElement ref = parserFacade.createReferenceFromText(referenceText, context);
      LOG.assertTrue(ref.isValid(), referenceText);
      return ResolveClassUtil.resolveClass(ref);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Override
  public PsiVariable resolveReferencedVariable(@NotNull String referenceText, PsiElement context) {
    return resolveVar(referenceText, context, null);
  }

  @Override
  public PsiVariable resolveAccessibleReferencedVariable(@NotNull String referenceText, PsiElement context) {
    final boolean[] problemWithAccess = new boolean[1];
    PsiVariable variable = resolveVar(referenceText, context, problemWithAccess);
    return problemWithAccess[0] ? null : variable;
  }

  @Nullable
  private PsiVariable resolveVar(final String referenceText, final PsiElement context, final boolean[] problemWithAccess) {
    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(myManager.getProject()).getParserFacade();
    try {
      final PsiJavaCodeReferenceElement ref = parserFacade.createReferenceFromText(referenceText, context);
      return ResolveVariableUtil.resolveVariable(ref, problemWithAccess, null);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Override
  public boolean isAccessible(@NotNull PsiMember member, @NotNull PsiElement place, @Nullable PsiClass accessObjectClass) {
    return isAccessible(member, member.getModifierList(), place, accessObjectClass, null);
  }

  @Override
  public boolean isAccessible(@NotNull PsiMember member,
                              @Nullable PsiModifierList modifierList,
                              @NotNull PsiElement place,
                              @Nullable PsiClass accessObjectClass,
                              @Nullable PsiElement currentFileResolveScope) {
    PsiClass containingClass = member.getContainingClass();
    return JavaResolveUtil.isAccessible(member, containingClass, modifierList, place, accessObjectClass, currentFileResolveScope);
  }

  @Override
  @NotNull
  public CandidateInfo[] getReferencedMethodCandidates(@NotNull PsiCallExpression expr, boolean dummyImplicitConstructor) {
    PsiFile containingFile = expr.getContainingFile();
    final MethodCandidatesProcessor processor = new MethodCandidatesProcessor(expr, containingFile);
    try {
      PsiScopesUtil.setupAndRunProcessor(processor, expr, dummyImplicitConstructor);
    }
    catch (MethodProcessorSetupFailedException e) {
      return CandidateInfo.EMPTY_ARRAY;
    }
    return processor.getCandidates();
  }

  @Override
  public PsiType inferTypeForMethodTypeParameter(@NotNull PsiTypeParameter typeParameter,
                                                 @NotNull PsiParameter[] parameters,
                                                 @NotNull PsiExpression[] arguments,
                                                 @NotNull PsiSubstitutor partialSubstitutor,
                                                 @Nullable PsiElement parent,
                                                 @NotNull ParameterTypeInferencePolicy policy) {
    return getInferenceHelper(PsiUtil.getLanguageLevel(parent != null ? parent : typeParameter))
      .inferTypeForMethodTypeParameter(typeParameter, parameters, arguments, partialSubstitutor, parent, policy);
  }

  @Override
  @NotNull
  public PsiSubstitutor inferTypeArguments(@NotNull PsiTypeParameter[] typeParameters,
                                           @NotNull PsiParameter[] parameters,
                                           @NotNull PsiExpression[] arguments,
                                           @NotNull PsiSubstitutor partialSubstitutor,
                                           @NotNull PsiElement parent,
                                           @NotNull ParameterTypeInferencePolicy policy) {
    return getInferenceHelper(PsiUtil.getLanguageLevel(parent))
      .inferTypeArguments(typeParameters, parameters, arguments, partialSubstitutor, parent, policy, PsiUtil.getLanguageLevel(parent));
  }

  @Override
  @NotNull
  public PsiSubstitutor inferTypeArguments(@NotNull PsiTypeParameter[] typeParameters,
                                           @NotNull PsiParameter[] parameters,
                                           @NotNull PsiExpression[] arguments,
                                           @NotNull PsiSubstitutor partialSubstitutor,
                                           @NotNull PsiElement parent,
                                           @NotNull ParameterTypeInferencePolicy policy,
                                           @NotNull LanguageLevel languageLevel) {
    return getInferenceHelper(languageLevel)
      .inferTypeArguments(typeParameters, parameters, arguments, partialSubstitutor, parent, policy, languageLevel);
  }

  @Override
  @NotNull
  public PsiSubstitutor inferTypeArguments(@NotNull PsiTypeParameter[] typeParameters,
                                           @NotNull PsiType[] leftTypes,
                                           @NotNull PsiType[] rightTypes,
                                           @NotNull LanguageLevel languageLevel) {
    return getInferenceHelper(languageLevel)
      .inferTypeArguments(typeParameters, leftTypes, rightTypes, languageLevel);
  }

  @Override
  public PsiType getSubstitutionForTypeParameter(PsiTypeParameter typeParam,
                                                 PsiType param,
                                                 PsiType arg,
                                                 boolean isContraVariantPosition, 
                                                 LanguageLevel languageLevel) {
    return getInferenceHelper(languageLevel)
      .getSubstitutionForTypeParameter(typeParam, param, arg, isContraVariantPosition, languageLevel);
  }

  private PsiInferenceHelper myTestHelper;

  public void setTestHelper(PsiInferenceHelper testHelper) {
    myTestHelper = testHelper;
  }

  public PsiInferenceHelper getInferenceHelper(LanguageLevel languageLevel) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myTestHelper != null ? myTestHelper : new PsiOldInferenceHelper(myManager);
    }
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8) && Registry.is("enable.graph.inference", false)) {
      return new PsiGraphInferenceHelper(myManager);
    }
    return new PsiOldInferenceHelper(myManager);
  }
}

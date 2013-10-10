/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.infos;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.impl.source.resolve.ParameterTypeInferencePolicy;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author ik, dsl
 */
public class MethodCandidateInfo extends CandidateInfo{
  public static final ThreadLocal<Map<PsiElement,  Pair<PsiMethod, PsiSubstitutor>>> CURRENT_CANDIDATE = new ThreadLocal<Map<PsiElement,  Pair<PsiMethod, PsiSubstitutor>>>();
  @ApplicabilityLevelConstant private int myApplicabilityLevel = 0;
  private final PsiElement myArgumentList;
  private final PsiType[] myArgumentTypes;
  private final PsiType[] myTypeArguments;
  private PsiSubstitutor myCalcedSubstitutor = null;
  private final LanguageLevel myLanguageLevel;

  public MethodCandidateInfo(PsiElement candidate,
                             PsiSubstitutor substitutor,
                             boolean accessProblem,
                             boolean staticsProblem,
                             PsiElement argumentList,
                             PsiElement currFileContext,
                             @Nullable PsiType[] argumentTypes,
                             PsiType[] typeArguments) {
    this(candidate, substitutor, accessProblem, staticsProblem, argumentList, currFileContext, argumentTypes, typeArguments,
         PsiUtil.getLanguageLevel(argumentList));
  }

  public MethodCandidateInfo(PsiElement candidate,
                             PsiSubstitutor substitutor,
                             boolean accessProblem,
                             boolean staticsProblem,
                             PsiElement argumentList,
                             PsiElement currFileContext,
                             @Nullable PsiType[] argumentTypes,
                             PsiType[] typeArguments,
                             @NotNull LanguageLevel languageLevel) {
    super(candidate, substitutor, accessProblem, staticsProblem, currFileContext);
    myArgumentList = argumentList;
    myArgumentTypes = argumentTypes;
    myTypeArguments = typeArguments;
    myLanguageLevel = languageLevel;
  }

  public boolean isApplicable(){
    return getApplicabilityLevel() != ApplicabilityLevel.NOT_APPLICABLE;
  }

  @ApplicabilityLevelConstant
  private int getApplicabilityLevelInner() {
    if (myArgumentTypes == null) return ApplicabilityLevel.NOT_APPLICABLE;

    int level = PsiUtil.getApplicabilityLevel(getElement(), getSubstitutor(), myArgumentTypes, myLanguageLevel);
    if (level > ApplicabilityLevel.NOT_APPLICABLE && !isTypeArgumentsApplicable()) level = ApplicabilityLevel.NOT_APPLICABLE;
    return level;
  }


  @ApplicabilityLevelConstant
  public int getApplicabilityLevel() {
    if(myApplicabilityLevel == 0){
      myApplicabilityLevel = getApplicabilityLevelInner();
    }
    return myApplicabilityLevel;
  }

  public PsiSubstitutor getSiteSubstitutor() {
    return super.getSubstitutor();
  }
  
  @Override
  public PsiSubstitutor getSubstitutor() {
    return getSubstitutor(true);
  }
  
  public PsiSubstitutor getSubstitutor(boolean includeReturnConstraint) {
    if (myCalcedSubstitutor == null || !includeReturnConstraint) {
      PsiSubstitutor incompleteSubstitutor = super.getSubstitutor();
      PsiMethod method = getElement();
      if (myTypeArguments == null) {
        final RecursionGuard.StackStamp stackStamp = PsiDiamondType.ourDiamondGuard.markStack();

        final PsiSubstitutor inferredSubstitutor = inferTypeArguments(DefaultParameterTypeInferencePolicy.INSTANCE, includeReturnConstraint);

         if (!stackStamp.mayCacheNow() || !includeReturnConstraint) {
          return inferredSubstitutor;
        }

        myCalcedSubstitutor = inferredSubstitutor;
      }
      else {
        PsiTypeParameter[] typeParams = method.getTypeParameters();
        for (int i = 0; i < myTypeArguments.length && i < typeParams.length; i++) {
          incompleteSubstitutor = incompleteSubstitutor.put(typeParams[i], myTypeArguments[i]);
        }
        myCalcedSubstitutor = incompleteSubstitutor;
      }
    }

    return myCalcedSubstitutor;
  }


  public boolean isTypeArgumentsApplicable() {
    final PsiMethod psiMethod = getElement();
    PsiTypeParameter[] typeParams = psiMethod.getTypeParameters();
    if (myTypeArguments != null && typeParams.length != myTypeArguments.length && !PsiUtil.isLanguageLevel7OrHigher(psiMethod)){
      return typeParams.length == 0 && JavaVersionService.getInstance().isAtLeast(psiMethod, JavaSdkVersion.JDK_1_7);
    }
    PsiSubstitutor substitutor = getSubstitutor();
    return GenericsUtil.isTypeArgumentsApplicable(typeParams, substitutor, getParent());
  }

  protected PsiElement getParent() {
    return myArgumentList != null ? myArgumentList.getParent() : myArgumentList;
  }

  @Override
  public boolean isValidResult(){
    return super.isValidResult() && isApplicable();
  }

  @Override
  public PsiMethod getElement(){
    return (PsiMethod)super.getElement();
  }

  public PsiSubstitutor inferTypeArguments(@NotNull ParameterTypeInferencePolicy policy, boolean includeReturnConstraint) {
    return inferTypeArguments(policy, myArgumentList instanceof PsiExpressionList
                                      ? ((PsiExpressionList)myArgumentList).getExpressions()
                                      : PsiExpression.EMPTY_ARRAY, includeReturnConstraint);
  }

  public PsiSubstitutor inferSubstitutorFromArgs(@NotNull ParameterTypeInferencePolicy policy, final PsiExpression[] arguments) {
    if (myTypeArguments == null) {
      return inferTypeArguments(policy, arguments, true);
    }
    else {
      PsiSubstitutor incompleteSubstitutor = super.getSubstitutor();
      PsiMethod method = getElement();
      if (method != null) {
        PsiTypeParameter[] typeParams = method.getTypeParameters();
        for (int i = 0; i < myTypeArguments.length && i < typeParams.length; i++) {
          incompleteSubstitutor = incompleteSubstitutor.put(typeParams[i], myTypeArguments[i]);
        }
      }
      return incompleteSubstitutor;
    }
  }

  public PsiSubstitutor inferTypeArguments(@NotNull ParameterTypeInferencePolicy policy,
                                           @NotNull PsiExpression[] arguments, 
                                           boolean includeReturnConstraint) {
    Map<PsiElement, Pair<PsiMethod, PsiSubstitutor>> map = CURRENT_CANDIDATE.get();
    if (map == null) {
      map = new ConcurrentWeakHashMap<PsiElement, Pair<PsiMethod, PsiSubstitutor>>();
      CURRENT_CANDIDATE.set(map);
    }
    final PsiMethod method = getElement();
    final Pair<PsiMethod, PsiSubstitutor> alreadyThere = includeReturnConstraint
                                                         ? map.put(getMarkerList(), Pair.create(method, super.getSubstitutor())) 
                                                         : null;
    try {
      PsiTypeParameter[] typeParameters = method.getTypeParameters();

      if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && PsiUtil.isRawSubstitutor(containingClass, mySubstitutor)) {
          Project project = containingClass.getProject();
          JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
          return javaPsiFacade.getElementFactory().createRawSubstitutor(mySubstitutor, typeParameters);
        }
      }

      final PsiElement parent = getParent();
      if (parent == null) return PsiSubstitutor.EMPTY;
      Project project = method.getProject();
      JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
      return javaPsiFacade.getResolveHelper()
        .inferTypeArguments(typeParameters, method.getParameterList().getParameters(), arguments, mySubstitutor, parent, policy, myLanguageLevel);
    }
    finally {
      if (alreadyThere == null) map.remove(getMarkerList());
    }
  }

  protected PsiElement getMarkerList() {
    return myArgumentList;
  }

  public boolean isInferencePossible() {
    return myArgumentList != null && myArgumentList.isValid();
  }


  public static Pair<PsiMethod, PsiSubstitutor> getCurrentMethod(PsiElement context) {
    final Map<PsiElement,Pair<PsiMethod,PsiSubstitutor>> currentMethodCandidates = CURRENT_CANDIDATE.get();
    return currentMethodCandidates != null ? currentMethodCandidates.get(context) : null;
  }

  public static class ApplicabilityLevel {
    public static final int NOT_APPLICABLE = 1;
    public static final int VARARGS = 2;
    public static final int FIXED_ARITY = 3;
  }

  @MagicConstant(intValues = {ApplicabilityLevel.NOT_APPLICABLE, ApplicabilityLevel.VARARGS, ApplicabilityLevel.FIXED_ARITY})
  public @interface ApplicabilityLevelConstant {
  }
}

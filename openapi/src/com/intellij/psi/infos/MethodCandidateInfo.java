/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;

/**
 * @author ik, dsl
 */
public class MethodCandidateInfo extends CandidateInfo{
  private int myApplicabilityLevel = 0;
  private PsiExpressionList myArgumentList;
  private PsiType[] myTypeArguments;
  private PsiSubstitutor myCalcedSubstitutor = null;

  public MethodCandidateInfo(PsiElement candidate,
                             PsiSubstitutor substitutor,
                             boolean accessProblem,
                             boolean staticsProblem,
                             PsiExpressionList argumentList,
                             PsiElement currFileContext,
                             PsiType[] typeArguments) {
    super(candidate, substitutor, accessProblem, staticsProblem, currFileContext);
    myArgumentList = argumentList;
    myTypeArguments = typeArguments;
  }

  public MethodCandidateInfo(PsiElement element, PsiSubstitutor substitutor) {
    super(element, substitutor, false, false);
    myApplicabilityLevel = ApplicabilityLevel.FIXED_ARITY;
    myArgumentList = null;
  }

  public boolean isApplicable(){
    if(myApplicabilityLevel == 0){
      myApplicabilityLevel = getApplicabilityLevelInner();
    }
    return myApplicabilityLevel != ApplicabilityLevel.NOT_APPLICABLE;
  }

  private int getApplicabilityLevelInner() {
    int level = PsiUtil.getApplicabilityLevel(getElement(), getSubstitutor(), myArgumentList);
    if (level > ApplicabilityLevel.NOT_APPLICABLE) {
      if (!isTypeArgumentsApplicable()) level = ApplicabilityLevel.NOT_APPLICABLE;
    }
    return level;
  }


  public int getApplicabilityLevel() {
    if(myApplicabilityLevel == 0){
      myApplicabilityLevel = getApplicabilityLevelInner();
    }
    return myApplicabilityLevel;
  }

  public PsiSubstitutor getSubstitutor() {
    if (myCalcedSubstitutor == null) {
      PsiSubstitutor incompleteSubstitutor = super.getSubstitutor();
      PsiMethod method = getElement();
      if (myTypeArguments == null) {
        myCalcedSubstitutor = inferTypeArguments(false);
      } else {
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
    PsiTypeParameter[] typeParams = getElement().getTypeParameters();
    if (myTypeArguments != null && typeParams.length != myTypeArguments.length) return false;
    PsiSubstitutor substitutor = getSubstitutor();
    return GenericsUtil.isTypeArgumentsApplicable(typeParams, substitutor);
  }

  public boolean isValidResult(){
    return super.isValidResult() && isApplicable();
  }

  public PsiMethod getElement(){
    return (PsiMethod)super.getElement();
  }

  public PsiSubstitutor inferTypeArguments(final boolean forCompletion) {
    PsiMethod method = getElement();
    PsiSubstitutor partialSubstitutor = mySubstitutor;
    PsiExpression[] arguments = myArgumentList == null ? PsiExpression.EMPTY_ARRAY : myArgumentList.getExpressions();
    PsiTypeParameter[] typeParameters = method.getTypeParameters();

    if (!method.hasModifierProperty(PsiModifier.STATIC) &&
        PsiUtil.isRawSubstitutor(method.getContainingClass(), partialSubstitutor)) {
      return createRawSubstitutor(partialSubstitutor, typeParameters);
    }

    PsiResolveHelper helper = method.getManager().getResolveHelper();
    return helper.inferTypeArguments(typeParameters,
                                                   method.getParameterList().getParameters(),
                                                   arguments,
                                                   partialSubstitutor,
                                                   myArgumentList.getParent(),
                                                   forCompletion);
  }

  private static PsiSubstitutor createRawSubstitutor(PsiSubstitutor substitutor, PsiTypeParameter[] typeParameters) {
    for (PsiTypeParameter typeParameter : typeParameters) {
      substitutor = substitutor.put(typeParameter, null);
    }

    return substitutor;
  }

  public static class ApplicabilityLevel {
    public static final int NOT_APPLICABLE = 1;
    public static final int VARARGS = 2;
    public static final int FIXED_ARITY = 3;
  }
}

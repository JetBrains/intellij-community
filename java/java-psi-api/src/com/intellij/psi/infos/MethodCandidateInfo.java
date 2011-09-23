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

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author ik, dsl
 */
public class MethodCandidateInfo extends CandidateInfo{
  private int myApplicabilityLevel = 0;
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
                             final LanguageLevel languageLevel) {
    super(candidate, substitutor, accessProblem, staticsProblem, currFileContext);
    myArgumentList = argumentList;
    myArgumentTypes = argumentTypes;
    myTypeArguments = typeArguments;
    myLanguageLevel = languageLevel;
  }

  public boolean isApplicable(){
    return getApplicabilityLevel() != ApplicabilityLevel.NOT_APPLICABLE;
  }

  private int getApplicabilityLevelInner() {
    if (myArgumentTypes == null) return ApplicabilityLevel.NOT_APPLICABLE;

    int level = PsiUtil.getApplicabilityLevel(getElement(), getSubstitutor(), myArgumentTypes, myLanguageLevel);
    if (level > ApplicabilityLevel.NOT_APPLICABLE && !isTypeArgumentsApplicable()) level = ApplicabilityLevel.NOT_APPLICABLE;
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
    PsiTypeParameter[] typeParams = getElement().getTypeParameters();
    if (myTypeArguments != null && typeParams.length != myTypeArguments.length) return false;
    PsiSubstitutor substitutor = getSubstitutor();
    return GenericsUtil.isTypeArgumentsApplicable(typeParams, substitutor, myArgumentList.getParent());
  }

  public boolean isValidResult(){
    return super.isValidResult() && isApplicable();
  }

  public PsiMethod getElement(){
    return (PsiMethod)super.getElement();
  }

  public PsiSubstitutor inferTypeArguments(final boolean forCompletion) {
    return inferTypeArguments(forCompletion, myArgumentList instanceof PsiExpressionList
                                             ? ((PsiExpressionList)myArgumentList).getExpressions()
                                             : PsiExpression.EMPTY_ARRAY);
  }

  public PsiSubstitutor inferTypeArguments(final boolean forCompletion, final PsiExpression[] arguments) {
    PsiMethod method = getElement();
    PsiTypeParameter[] typeParameters = method.getTypeParameters();

    JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(method.getProject());
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && PsiUtil.isRawSubstitutor(containingClass, mySubstitutor)) {
        return javaPsiFacade.getElementFactory().createRawSubstitutor(mySubstitutor, typeParameters);
      }
    }

    return javaPsiFacade.getResolveHelper().inferTypeArguments(typeParameters, method.getParameterList().getParameters(), arguments, mySubstitutor,
                                     myArgumentList.getParent(), forCompletion);
  }

  public static class ApplicabilityLevel {
    public static final int NOT_APPLICABLE = 1;
    public static final int VARARGS = 2;
    public static final int FIXED_ARITY = 3;
  }
}

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.infos;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;

/**
 * @author ik, dsl
 */
public class MethodCandidateInfo extends CandidateInfo{
  private Boolean myApplicableFlag = null;
  private PsiExpressionList myArgumentList;
  private PsiType[] myTypeArguments;
  private PsiSubstitutor myCalcedSubstitutor = null;

  public MethodCandidateInfo(PsiElement candidate,
                             PsiSubstitutor substitutor,
                             PsiElement place,
                             PsiClass psiClass,
                             boolean staticsProblem,
                             PsiExpressionList argumentList,
                             final PsiElement currFileContext,
                             PsiType[] typeArguments) {
    super(candidate, substitutor, place, psiClass, staticsProblem, currFileContext);
    myArgumentList = argumentList;
    myTypeArguments = typeArguments;
  }

  public MethodCandidateInfo(PsiElement element, PsiSubstitutor substitutor) {
    super(element, substitutor, false, false);
    myApplicableFlag = Boolean.TRUE;
    myArgumentList = null;
  }

  public boolean isApplicable(){
    if(myApplicableFlag == null){
      boolean applicable = PsiUtil.isApplicable(getElement(), getSubstitutor(), myArgumentList) &&
                               isTypeArgumentsApplicable();
      myApplicableFlag = applicable ? Boolean.TRUE : Boolean.FALSE;
    }
    return myApplicableFlag.booleanValue();
  }

  public PsiSubstitutor getSubstitutor() {
    if (myCalcedSubstitutor == null) {
      PsiSubstitutor incompleteSubstitutor = super.getSubstitutor();
      PsiMethod method = getElement();
      if (myTypeArguments == null) {
        myCalcedSubstitutor = inferTypeArguments(method, incompleteSubstitutor);
      } else {
        PsiTypeParameter[] typeParams = method.getTypeParameterList().getTypeParameters();
        for (int i = 0; i < myTypeArguments.length && i < typeParams.length; i++) {
          incompleteSubstitutor = incompleteSubstitutor.put(typeParams[i], myTypeArguments[i]);
        }
        myCalcedSubstitutor = incompleteSubstitutor;
      }
    }

    return myCalcedSubstitutor;
  }


  public boolean isTypeArgumentsApplicable() {
    PsiTypeParameter[] typeParams = getElement().getTypeParameterList().getTypeParameters();
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

  private PsiSubstitutor inferTypeArguments(final PsiMethod method, PsiSubstitutor substitutor) {

    PsiExpression[] arguments = myArgumentList == null ? PsiExpression.EMPTY_ARRAY : myArgumentList.getExpressions();
    PsiTypeParameter[] typeParameters = method.getTypeParameterList().getTypeParameters();

    if (method.getSignature(substitutor).isRaw()) {
      return createRawSubstitutor(substitutor, typeParameters);
    }

    PsiResolveHelper helper = method.getManager().getResolveHelper();
    for(int i = 0; i < typeParameters.length; i++){
      final PsiTypeParameter typeParameter = typeParameters[i];
      final PsiParameter[] parameters = method.getParameterList().getParameters();

      PsiType substitution = helper.inferTypeForMethodTypeParameter(typeParameter, parameters, arguments, 
                                                             substitutor, myArgumentList.getParent());

      if (substitution == null) return createRawSubstitutor(substitutor, typeParameters);
      if (substitution == PsiType.NULL) continue;

      if (substitution == PsiType.NULL || substitution == PsiType.VOID) substitution = null;
      substitutor = substitutor.put (typeParameter, substitution);
    }
    return substitutor;
  }

  private PsiSubstitutor createRawSubstitutor(PsiSubstitutor substitutor, PsiTypeParameter[] typeParameters) {
    for (int i = 0; i < typeParameters.length; i++) {
      substitutor = substitutor.put(typeParameters[i], null);
    }

    return substitutor;
  }

}

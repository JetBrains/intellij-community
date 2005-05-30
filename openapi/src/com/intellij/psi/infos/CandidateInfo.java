/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.infos;

import com.intellij.psi.*;

/**
 * @author ik,dsl
 */
public class CandidateInfo implements JavaResolveResult {
  private PsiElement myPlace = null;
  private PsiClass myAccessClass = null;
  private PsiElement myCandidate = null;
  private Boolean myAccessProblem = null;
  private boolean myStaticsProblem = false;
  private PsiSubstitutor mySubstitutor = null;
  private PsiElement myCurrentFileResolveContext = null;
  public static CandidateInfo[] EMPTY_ARRAY = new CandidateInfo[0];
  private boolean myPackagePrefixPackageReference;

  public CandidateInfo(PsiElement candidate, PsiSubstitutor substitutor, boolean accessProblem, boolean staticsProblem, PsiElement currFileContext){
    myCandidate = candidate;
    myAccessProblem = accessProblem ? Boolean.TRUE : Boolean.FALSE;
    myStaticsProblem = staticsProblem;
    mySubstitutor = substitutor;
    myCurrentFileResolveContext = currFileContext;
  }

  public CandidateInfo(PsiElement candidate, PsiSubstitutor substitutor, boolean accessProblem, boolean staticsProblem){
    this(candidate, substitutor, accessProblem, staticsProblem, null);
  }

  public CandidateInfo(PsiElement candidate, PsiSubstitutor substitutor, PsiElement place, boolean staticsProblem){
    myStaticsProblem = staticsProblem;
    myPlace = place;
    mySubstitutor = substitutor;
    myCandidate = candidate;
  }

  public CandidateInfo(PsiElement candidate,
                       PsiSubstitutor substitutor,
                       PsiElement place,
                       PsiClass accessClass,
                       boolean staticsProblem,
                       PsiElement currFileContext){
    myStaticsProblem = staticsProblem;
    myAccessClass = accessClass;
    myPlace = place;
    mySubstitutor = substitutor;
    myCandidate = candidate;
    myCurrentFileResolveContext = currFileContext;
  }

  public CandidateInfo(PsiElement candidate, PsiSubstitutor substitutor){
    myCandidate = candidate;
    mySubstitutor = substitutor;
  }

  public CandidateInfo(CandidateInfo candidate, PsiSubstitutor newSubstitutor){
    myPlace = candidate.myPlace;
    myCandidate = candidate.myCandidate;
    myAccessProblem = candidate.myAccessProblem;
    myStaticsProblem = candidate.myStaticsProblem;
    myCurrentFileResolveContext = candidate.myCurrentFileResolveContext;
    mySubstitutor = newSubstitutor;
  }

  public boolean isValidResult(){
    return isAccessible() && isStaticsScopeCorrect();
  }

  public boolean isPackagePrefixPackageReference() {
    return myPackagePrefixPackageReference;
  }

  public PsiElement getElement(){
    return myCandidate;
  }

  public PsiSubstitutor getSubstitutor(){
    return mySubstitutor;
  }

  public boolean isAccessible(){
    if(myAccessProblem == null){
      boolean accessProblem = false;
      if (myPlace != null && myCandidate instanceof PsiMember) {
        accessProblem = !myPlace.getManager().getResolveHelper().isAccessible((PsiMember)myCandidate, myPlace, myAccessClass);
      }
      myAccessProblem = accessProblem ? Boolean.TRUE : Boolean.FALSE;
    }
    return !myAccessProblem.booleanValue();
  }

  public boolean isStaticsScopeCorrect(){
    return !myStaticsProblem;
  }

  public PsiElement getCurrentFileResolveScope() {
    return myCurrentFileResolveContext;
  }

  private void setPackagePrefixPackageReference(boolean packagePrefixPackageReference) {
    myPackagePrefixPackageReference = packagePrefixPackageReference;
  }

  public static CandidateInfo createCandidateInfoForPackagePrefixPart() {
    final CandidateInfo candidateInfo = new CandidateInfo(null, PsiSubstitutor.EMPTY, false, false);
    candidateInfo.setPackagePrefixPackageReference(true);
    return candidateInfo;
  }

  public static final JavaResolveResult[] RESOLVE_RESULT_FOR_PACKAGE_PREFIX_PACKAGE =
          new JavaResolveResult[]{createCandidateInfoForPackagePrefixPart()};
}

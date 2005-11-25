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

/**
 * @author ik,dsl
 */
public class CandidateInfo implements JavaResolveResult {
  public static CandidateInfo[] EMPTY_ARRAY = new CandidateInfo[0];

  private final PsiElement myPlace;
  private final PsiMember myAccessMember;
  private final PsiElement myCandidate;
  private Boolean myAccessProblem = null;
  private final boolean myStaticsProblem;
  protected final PsiSubstitutor mySubstitutor;
  private final PsiElement myCurrentFileResolveContext;
  private boolean myPackagePrefixPackageReference;

  public CandidateInfo(PsiElement candidate, PsiSubstitutor substitutor, boolean accessProblem, boolean staticsProblem, PsiElement currFileContext){
    myCandidate = candidate;
    myAccessProblem = accessProblem ? Boolean.TRUE : Boolean.FALSE;
    myStaticsProblem = staticsProblem;
    mySubstitutor = substitutor;
    myCurrentFileResolveContext = currFileContext;
    myAccessMember = null;
    myPlace = null;
  }

  public CandidateInfo(PsiElement candidate, PsiSubstitutor substitutor, boolean accessProblem, boolean staticsProblem){
    this(candidate, substitutor, accessProblem, staticsProblem, null);
  }

  public CandidateInfo(PsiElement candidate, PsiSubstitutor substitutor, PsiElement place, boolean staticsProblem){
    this(candidate, substitutor, place, null, staticsProblem, null);
  }

  public CandidateInfo(PsiElement candidate,
                       PsiSubstitutor substitutor,
                       PsiElement place,
                       PsiMember accessMember,
                       boolean staticsProblem,
                       PsiElement currFileContext){
    myStaticsProblem = staticsProblem;
    myAccessMember = accessMember;
    myPlace = place;
    mySubstitutor = substitutor;
    myCandidate = candidate;
    myCurrentFileResolveContext = currFileContext;
  }

  public CandidateInfo(PsiElement candidate, PsiSubstitutor substitutor){
    this(candidate, substitutor, null, null, false, null);
  }

  public CandidateInfo(CandidateInfo candidate, PsiSubstitutor newSubstitutor){
    this(candidate.myCandidate, newSubstitutor, candidate.myPlace, null, candidate.myStaticsProblem, candidate.myCurrentFileResolveContext);
    myAccessProblem = candidate.myAccessProblem;
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
        final PsiMember member = (PsiMember)myCandidate;
        accessProblem = !myPlace.getManager().getResolveHelper().isAccessible(member, member.getModifierList(), myPlace, myAccessMember, myCurrentFileResolveContext);
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

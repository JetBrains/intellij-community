/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.infos;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 06.10.2003
 * Time: 19:28:22
 * To change this template use Options | File Templates.
 */
public class ClassCandidateInfo extends CandidateInfo{
  private boolean myGrouped;
  public ClassCandidateInfo(PsiElement candidate, PsiSubstitutor substitutor, boolean accessProblem, boolean grouped, PsiElement currFileContext){
    super(candidate, substitutor, accessProblem, false, currFileContext);
    myGrouped = grouped;
  }

  public ClassCandidateInfo(PsiElement candidate, PsiSubstitutor substitutor){
    super(candidate, substitutor, false, false);
    myGrouped = false;
  }


  public boolean isGrouped(){
    return myGrouped;
  }

  public PsiClass getCandidate(){
    return (PsiClass) getElement();
  }
}

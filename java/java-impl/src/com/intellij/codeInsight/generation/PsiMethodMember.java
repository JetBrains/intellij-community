/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.generation;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiFormatUtil;

/**
 * @author peter
*/
public class PsiMethodMember extends PsiElementClassMember<PsiMethod>{
  private static final int PARAM_OPTIONS = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER;
  private static final int METHOD_OPTIONS = PARAM_OPTIONS | PsiFormatUtil.SHOW_PARAMETERS;

  public PsiMethodMember(final PsiMethod method) {
    this(method, PsiSubstitutor.EMPTY);
  }

  public PsiMethodMember(CandidateInfo info) {
    this((PsiMethod)info.getElement(), info.getSubstitutor());
  }

  public PsiMethodMember(final PsiMethod method, final PsiSubstitutor substitutor) {
    super(method, substitutor, PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, METHOD_OPTIONS, PARAM_OPTIONS));
  }

}

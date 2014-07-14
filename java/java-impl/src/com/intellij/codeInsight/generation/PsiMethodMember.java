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
package com.intellij.codeInsight.generation;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PsiMethodMember extends PsiElementClassMember<PsiMethod>{
  private static final int PARAM_OPTIONS = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER;
  private static final int METHOD_OPTIONS = PARAM_OPTIONS | PsiFormatUtilBase.SHOW_PARAMETERS;

  public PsiMethodMember(@NotNull PsiMethod method) {
    this(method, PsiSubstitutor.EMPTY);
  }

  public PsiMethodMember(@NotNull CandidateInfo info) {
    this((PsiMethod)info.getElement(), info.getSubstitutor());
  }

  public PsiMethodMember(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor) {
    super(method, substitutor, PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, METHOD_OPTIONS, PARAM_OPTIONS));
  }

}

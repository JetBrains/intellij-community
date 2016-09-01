/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public class MemberLookupHelper {
  private final PsiMember myMember;
  private final boolean myMergedOverloads;
  @Nullable private final PsiClass myContainingClass;
  private boolean myShouldImport;

  public MemberLookupHelper(List<PsiMethod> overloads, PsiClass containingClass, boolean shouldImport) {
    this(overloads.get(0), containingClass, shouldImport, true);
  }

  public MemberLookupHelper(PsiMember member, @Nullable PsiClass containingClass, boolean shouldImport, final boolean mergedOverloads) {
    myMember = member;
    myContainingClass = containingClass;
    myShouldImport = shouldImport;
    myMergedOverloads = mergedOverloads;
  }

  public PsiMember getMember() {
    return myMember;
  }

  @Nullable
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  public void setShouldBeImported(boolean shouldImportStatic) {
    myShouldImport = shouldImportStatic;
  }

  public boolean willBeImported() {
    return myShouldImport;
  }

  public void renderElement(LookupElementPresentation presentation, boolean showClass, boolean showPackage, PsiSubstitutor substitutor) {
    final String className = myContainingClass == null ? "???" : myContainingClass.getName();

    final String memberName = myMember.getName();
    if (showClass && StringUtil.isNotEmpty(className)) {
      presentation.setItemText(className + "." + memberName);
    } else {
      presentation.setItemText(memberName);
    }

    final String qname = myContainingClass == null ? "" : myContainingClass.getQualifiedName();
    String pkg = qname == null ? "" : StringUtil.getPackageName(qname);
    String location = showPackage && StringUtil.isNotEmpty(pkg) ? " (" + pkg + ")" : "";

    final String params = myMergedOverloads
                          ? "(...)"
                          : myMember instanceof PsiMethod
                            ? getMethodParameterString((PsiMethod)myMember, substitutor)
                            : "";
    presentation.clearTail();
    presentation.appendTailText(params, false);
    if (myShouldImport && StringUtil.isNotEmpty(className)) {
      presentation.appendTailText(" in " + className + location, true);
    } else {
      presentation.appendTailText(location, true);
    }

    final PsiType type = myMember instanceof PsiMethod ? ((PsiMethod)myMember).getReturnType() : ((PsiField) myMember).getType();
    if (type != null) {
      presentation.setTypeText(substitutor.substitute(type).getPresentableText());
    }
  }

  @NotNull
  static String getMethodParameterString(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor) {
    return PsiFormatUtil.formatMethod(method, substitutor,
                                      PsiFormatUtilBase.SHOW_PARAMETERS, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE);
  }
}

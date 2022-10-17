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
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class MemberLookupHelper {
  private final PsiMember myMember;
  private final boolean myMergedOverloads;
  @Nullable private final PsiClass myContainingClass;
  private boolean myShouldImport;

  public MemberLookupHelper(List<? extends PsiMethod> overloads, PsiClass containingClass, boolean shouldImport) {
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
    boolean constructor = myMember instanceof PsiMethod && ((PsiMethod)myMember).isConstructor();
    if (constructor) {
      presentation.setItemText("new " + memberName);
      if (myContainingClass != null && myContainingClass.getTypeParameters().length > 0) {
        presentation.appendTailText("<>", false);
      }
    }
    else if (showClass && StringUtil.isNotEmpty(className)) {
      presentation.setItemText(className + "." + memberName);
    }
    else {
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

    presentation.appendTailText(params, false);
    if (myShouldImport && !constructor && StringUtil.isNotEmpty(className)) {
      presentation.appendTailText(" in " + className + location, true);
    } else {
      presentation.appendTailText(location, true);
    }

    PsiType type = getDeclaredType(myMember, substitutor);
    if (type != null) {
      presentation.setTypeText(type.getPresentableText());
    }
  }

  @Nullable
  static PsiType getDeclaredType(PsiMember member, PsiSubstitutor substitutor) {
    if (member instanceof PsiField) {
      return substitutor.substitute(((PsiField)member).getType());
    }
    if (member instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)member;
      if (method.isConstructor()) {
        PsiClass aClass = Objects.requireNonNull(method.getContainingClass());
        return JavaPsiFacade.getElementFactory(method.getProject()).createType(aClass, substitutor);
      }
      return patchGetClass(method, substitutor.substitute(method.getReturnType()));
    }
    return null;
  }

  @Nullable
  private static PsiType patchGetClass(@NotNull PsiMethod method, @Nullable PsiType type) {
    if (PsiTypesUtil.isGetClass(method) && type instanceof PsiClassType) {
      PsiType arg = ContainerUtil.getFirstItem(Arrays.asList(((PsiClassType)type).getParameters()));
      PsiType bound = arg instanceof PsiWildcardType ? TypeConversionUtil.erasure(((PsiWildcardType)arg).getExtendsBound()) : null;
      if (bound != null) {
        return PsiTypesUtil.createJavaLangClassType(method, bound, false);
      }
    }
    return type;
  }

  @NotNull
  static String getMethodParameterString(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor) {
    return PsiFormatUtil.formatMethod(method, substitutor,
                                      PsiFormatUtilBase.SHOW_PARAMETERS, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE);
  }
}

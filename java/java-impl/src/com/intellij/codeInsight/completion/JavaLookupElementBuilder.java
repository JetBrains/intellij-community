/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class JavaLookupElementBuilder {
  private JavaLookupElementBuilder() {
  }

  public static LookupElementBuilder forField(@NotNull PsiField field) {
    return forField(field, field.getName(), null);
  }

  public static LookupElementBuilder forField(@NotNull PsiField field,
                                              final String lookupString,
                                              final @Nullable PsiClass qualifierClass) {
    final LookupElementBuilder builder = LookupElementBuilder.create(field, lookupString).setIcon(field.getIcon(Iconable.ICON_FLAG_VISIBILITY));
    return setBoldIfInClass(field, qualifierClass, builder);
  }

  public static LookupElementBuilder forMethod(@NotNull PsiMethod method, final PsiSubstitutor substitutor) {
    return forMethod(method, method.getName(), substitutor, null);
  }

  public static LookupElementBuilder forMethod(@NotNull PsiMethod method,
                                               @NotNull String lookupString, final @NotNull PsiSubstitutor substitutor,
                                               @Nullable PsiClass qualifierClass) {
    LookupElementBuilder builder = LookupElementBuilder.create(method, lookupString)
      .setIcon(method.getIcon(Iconable.ICON_FLAG_VISIBILITY))
      .setPresentableText(method.getName())
      .setTailText(PsiFormatUtil.formatMethod(method, substitutor,
                                              PsiFormatUtil.SHOW_PARAMETERS,
                                              PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE));
    final PsiType returnType = method.getReturnType();
    if (returnType != null) {
      builder = builder.setTypeText(substitutor.substitute(returnType).getPresentableText());
    }
    builder = setBoldIfInClass(method, qualifierClass, builder);
    return builder;
  }

  private static LookupElementBuilder setBoldIfInClass(@NotNull PsiMember member, @Nullable PsiClass psiClass, @NotNull LookupElementBuilder builder) {
    if (psiClass != null && member.getManager().areElementsEquivalent(member.getContainingClass(), psiClass)) {
      return builder.setBold();
    }
    return builder;
  }

  public static LookupElementBuilder forClass(@NotNull PsiClass psiClass) {
    return forClass(psiClass, psiClass.getName());
  }

  public static LookupElementBuilder forClass(@NotNull PsiClass psiClass,
                                              final String lookupString) {
    return forClass(psiClass, lookupString, false);
  }

  public static LookupElementBuilder forClass(@NotNull PsiClass psiClass,
                                              final String lookupString,
                                              final boolean withLocation) {
    final LookupElementBuilder builder =
      LookupElementBuilder.create(psiClass, lookupString).setIcon(psiClass.getIcon(Iconable.ICON_FLAG_VISIBILITY));
    if (withLocation) {
      return builder.setTailText(" (" + PsiFormatUtil.getPackageDisplayName(psiClass) + ")", true);
    }
    return builder;
  }
}

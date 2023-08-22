// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaLookupElementBuilder {
  private JavaLookupElementBuilder() {
  }

  public static LookupElementBuilder forField(@NotNull PsiField field) {
    return forField(field, field.getName(), null);
  }

  public static LookupElementBuilder forField(@NotNull PsiField field,
                                              final String lookupString,
                                              final @Nullable PsiClass qualifierClass) {
    final LookupElementBuilder builder = LookupElementBuilder.create(field, lookupString).withIcon(
      field.getIcon(Iconable.ICON_FLAG_VISIBILITY));
    return setBoldIfInClass(field, qualifierClass, builder);
  }

  public static LookupElementBuilder forMethod(@NotNull PsiMethod method, final PsiSubstitutor substitutor) {
    return forMethod(method, method.getName(), substitutor, null);
  }

  public static LookupElementBuilder forMethod(@NotNull PsiMethod method,
                                               @NotNull String lookupString, final @NotNull PsiSubstitutor substitutor,
                                               @Nullable PsiClass qualifierClass) {
    LookupElementBuilder builder = LookupElementBuilder.create(method, lookupString)
      .withIcon(method.getIcon(Iconable.ICON_FLAG_VISIBILITY))
      .withPresentableText(method.getName())
      .withTailText(PsiFormatUtil.formatMethod(method, substitutor,
                                               PsiFormatUtilBase.SHOW_PARAMETERS,
                                               PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE));
    final PsiType returnType = method.getReturnType();
    if (returnType != null) {
      builder = builder.withTypeText(substitutor.substitute(returnType).getPresentableText());
    }
    builder = setBoldIfInClass(method, qualifierClass, builder);
    return builder;
  }

  private static LookupElementBuilder setBoldIfInClass(@NotNull PsiMember member, @Nullable PsiClass psiClass, @NotNull LookupElementBuilder builder) {
    if (psiClass != null && member.getManager().areElementsEquivalent(member.getContainingClass(), psiClass)) {
      return builder.bold();
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
    LookupElementBuilder builder =
      LookupElementBuilder.create(psiClass, lookupString).withIcon(psiClass.getIcon(Iconable.ICON_FLAG_VISIBILITY));
    String name = psiClass.getName();
    if (StringUtil.isNotEmpty(name)) {
      builder = builder.withLookupString(name);
    }
    if (withLocation) {
      return builder.withTailText(" (" + PsiFormatUtil.getPackageDisplayName(psiClass) + ")", true);
    }
    return builder;
  }
}

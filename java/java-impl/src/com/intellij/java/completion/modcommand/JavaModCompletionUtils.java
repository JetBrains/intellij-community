// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Utilities to support mod command completion in Java
 */
@NotNullByDefault
public final class JavaModCompletionUtils {
  /**
   * @param type type to represent as {@link MarkupText}
   * @return markup text that represents the type
   */
  public static MarkupText typeMarkup(@Nullable PsiType type) {
    return type == null ? MarkupText.empty() :
           MarkupText.plainText(type.getPresentableText());
  }

  public static CommonCompletionItem forField(PsiField field) {
    return forField(field, field.getName(), null);
  }

  public static CommonCompletionItem forField(PsiField field,
                                              @NlsSafe String lookupString,
                                              @Nullable PsiClass qualifierClass) {
    MarkupText mainText =
      MarkupText.plainText(lookupString)
        .highlightAll(isInClass(field, qualifierClass) ? MarkupText.Kind.STRONG : MarkupText.Kind.NORMAL);
    return new CommonCompletionItem(lookupString)
      .withObject(field)
      .withPresentation(new ModCompletionItemPresentation(mainText)
                          .withMainIcon(() -> field.getIcon(Iconable.ICON_FLAG_VISIBILITY)));
  }

  public static CommonCompletionItem forMethod(PsiMethod method, final PsiSubstitutor substitutor) {
    return forMethod(method, method.getName(), substitutor, null);
  }

  public static CommonCompletionItem forMethod(PsiMethod method,
                                               @NlsSafe String lookupString, 
                                               PsiSubstitutor substitutor,
                                               @Nullable PsiClass qualifierClass) {
    String parametersText = PsiFormatUtil.formatMethod(method, substitutor,
                                                       PsiFormatUtilBase.SHOW_PARAMETERS,
                                                       PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE);
    MarkupText mainText =
      MarkupText.plainText(method.getName())
        .highlightAll(isInClass(method, qualifierClass) ? MarkupText.Kind.STRONG : MarkupText.Kind.NORMAL)
        .concat(parametersText, MarkupText.Kind.NORMAL);
    return new CommonCompletionItem(lookupString)
      .withObject(method)
      .withPresentation(new ModCompletionItemPresentation(mainText)
                          .withMainIcon(() -> method.getIcon(Iconable.ICON_FLAG_VISIBILITY))
                          .withDetailText(typeMarkup(method.getReturnType())));
  }

  private static boolean isInClass(PsiMember member, @Nullable PsiClass psiClass) {
    return psiClass != null && member.getManager().areElementsEquivalent(member.getContainingClass(), psiClass);
  }

  public static CommonCompletionItem forClass(PsiClass psiClass) {
    return forClass(psiClass, Objects.requireNonNull(psiClass.getName()));
  }

  public static CommonCompletionItem forClass(PsiClass psiClass,
                                              @NlsSafe String lookupString) {
    return forClass(psiClass, lookupString, false);
  }

  public static CommonCompletionItem forClass(PsiClass psiClass,
                                              @NlsSafe String lookupString,
                                              boolean withLocation) {
    MarkupText mainText = MarkupText.plainText(lookupString);
    if (withLocation) {
      mainText = mainText.concat(" (" + PsiFormatUtil.getPackageDisplayName(psiClass) + ")", MarkupText.Kind.GRAYED);
    }
    ModCompletionItemPresentation presentation = new ModCompletionItemPresentation(mainText)
      .withMainIcon(() -> psiClass.getIcon(Iconable.ICON_FLAG_VISIBILITY));
    CommonCompletionItem builder =
      new CommonCompletionItem(lookupString)
        .withObject(psiClass)
        .withPresentation(presentation);
    String name = psiClass.getName();
    if (StringUtil.isNotEmpty(name)) {
      builder = builder.addLookupString(name);
    }
    return builder;
  }
}

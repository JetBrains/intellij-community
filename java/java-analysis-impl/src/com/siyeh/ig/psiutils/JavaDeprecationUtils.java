// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.roots.JdkUtils;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaDeprecationUtils {
  private static @NotNull ThreeState isDeprecatedByAnnotation(@NotNull PsiModifierListOwner owner, @Nullable PsiElement context) {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, CommonClassNames.JAVA_LANG_DEPRECATED);
    if (annotation == null) return ThreeState.UNSURE;
    if (context == null) return ThreeState.YES;
    String since = null;
    PsiAnnotationMemberValue value = annotation.findAttributeValue("since");
    if (value instanceof PsiLiteralExpression) {
      since = ObjectUtils.tryCast(((PsiLiteralExpression)value).getValue(), String.class);
    }
    if (since == null || JdkUtils.getJdkForElement(owner) == null) return ThreeState.YES;
    LanguageLevel deprecationLevel = LanguageLevel.parse(since);
    return ThreeState.fromBoolean(deprecationLevel == null || PsiUtil.getLanguageLevel(context).isAtLeast(deprecationLevel));
  }

  
  /**
   * Checks if the given PSI element is deprecated with annotation or JavaDoc tag, taking the context into account.
   * <br>
   * It is suitable for elements other than {@link PsiDocCommentOwner}.
   * The deprecation of JDK members may depend on context. E.g., uses if a JDK method is deprecated since Java 19,
   * but current module has Java 17 target, than the method is not considered as deprecated.
   * 
   * @param psiElement element to check whether it's deprecated
   * @param context context in which the check should be performed
   */
  public static boolean isDeprecated(@NotNull PsiElement psiElement, @Nullable PsiElement context) {
    if (psiElement instanceof PsiModifierListOwner) {
      ThreeState byAnnotation = isDeprecatedByAnnotation((PsiModifierListOwner)psiElement, context);
      if (byAnnotation != ThreeState.UNSURE) {
        return byAnnotation.toBoolean();
      }
    }
    if (psiElement instanceof PsiDocCommentOwner) {
      return ((PsiDocCommentOwner)psiElement).isDeprecated();
    }
    if (psiElement instanceof PsiJavaDocumentedElement) {
      return PsiImplUtil.isDeprecatedByDocTag((PsiJavaDocumentedElement)psiElement);
    }
    return false;
  }
}

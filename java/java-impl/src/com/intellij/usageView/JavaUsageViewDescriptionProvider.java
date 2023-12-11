// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usageView;

import com.intellij.core.JavaPsiBundle;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public final class JavaUsageViewDescriptionProvider implements ElementDescriptionProvider {

  public static final String NO_NAME_CLASS_VALUE = "";
  private static final @NlsSafe String CLINIT = "<clinit>";
  private static final @NlsSafe String INIT = "<init>";

  @Override
  public String getElementDescription(@NotNull final PsiElement element, @NotNull final ElementDescriptionLocation location) {
    if (location instanceof UsageViewShortNameLocation) {
      if (element instanceof PsiThrowStatement) {
        return JavaBundle.message("usage.target.exception");
      }
      else if (element instanceof PsiAnonymousClass anonymousClass) {
        String name = anonymousClass.getBaseClassReference().getReferenceName();
        return getAnonymousClassName(name);
      }
      else if (element instanceof PsiClassInitializer initializer) {
        boolean isStatic = initializer.hasModifierProperty(PsiModifier.STATIC);
        return isStatic ? CLINIT : INIT;
      }
      else if (element instanceof PsiImplicitClass implicitClass) {
        return implicitClass.getContainingFile().getName();
      }
    }

    if (location instanceof UsageViewLongNameLocation) {
      if (element instanceof PsiPackage) {
        return ((PsiPackage)element).getQualifiedName();
      }
      else if (element instanceof PsiClass aClass) {
        if (element instanceof PsiAnonymousClass anonymousClass) {
          String name = anonymousClass.getBaseClassReference().getReferenceName();
          return getAnonymousClassName(name);
        }
        else {
          String ret = aClass.getQualifiedName(); // It happens for local classes
          if (ret == null) {
            ret = aClass.getName();
          }
          @NonNls String finalName = ObjectUtils.notNull(ret, NO_NAME_CLASS_VALUE);
          return finalName;
        }
      }
      else if (element instanceof PsiVariable var) {
        return var.getName();
      }
      else if (element instanceof PsiMethod psiMethod) {
        return PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                          PsiFormatUtilBase.SHOW_TYPE);
      }
    }

    return null;
  }

  @NotNull
  @Nls
  private static String getAnonymousClassName(@Nls String name) {
    return name != null ? JavaPsiBundle.message("java.terms.anonymous.class.base.ref", name)
                        : JavaElementKind.ANONYMOUS_CLASS.subject();
  }
}

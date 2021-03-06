/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/**
 * @author yole
 */
public class JavaUsageViewDescriptionProvider implements ElementDescriptionProvider {

  public static final String NO_NAME_CLASS_VALUE = "";
  private static final @NlsSafe String CLINIT = "<clinit>";
  private static final @NlsSafe String INIT = "<init>";

  @Override
  public String getElementDescription(@NotNull final PsiElement element, @NotNull final ElementDescriptionLocation location) {
    if (location instanceof UsageViewShortNameLocation) {
      if (element instanceof PsiThrowStatement) {
        return JavaBundle.message("usage.target.exception");
      }
      else if (element instanceof PsiAnonymousClass) {
        String name = ((PsiAnonymousClass)element).getBaseClassReference().getReferenceName();
        return getAnonymousClassName(name);
      }
      else if (element instanceof PsiClassInitializer) {
        boolean isStatic = ((PsiClassInitializer)element).hasModifierProperty(PsiModifier.STATIC);
        return isStatic ? CLINIT : INIT;
      }
    }

    if (location instanceof UsageViewLongNameLocation) {
      if (element instanceof PsiPackage) {
        return ((PsiPackage)element).getQualifiedName();
      }
      else if (element instanceof PsiClass) {
        if (element instanceof PsiAnonymousClass) {
          String name = ((PsiAnonymousClass)element).getBaseClassReference().getReferenceName();
          return getAnonymousClassName(name);
        }
        else {
          String ret = ((PsiClass)element).getQualifiedName(); // It happens for local classes
          if (ret == null) {
            ret = ((PsiClass)element).getName();
          }
          @NonNls String finalName = ObjectUtils.notNull(ret, NO_NAME_CLASS_VALUE);
          return finalName;
        }
      }
      else if (element instanceof PsiVariable) {
        return ((PsiVariable)element).getName();
      }
      else if (element instanceof PsiMethod) {
        PsiMethod psiMethod = (PsiMethod)element;
        return PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY,
                                          PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS, PsiFormatUtilBase.SHOW_TYPE);
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

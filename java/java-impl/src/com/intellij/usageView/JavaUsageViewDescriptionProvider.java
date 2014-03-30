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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.lang.LangBundle;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class JavaUsageViewDescriptionProvider implements ElementDescriptionProvider {
  @Override
  public String getElementDescription(@NotNull final PsiElement element, @NotNull final ElementDescriptionLocation location) {
    if (location instanceof UsageViewShortNameLocation) {
      if (element instanceof PsiThrowStatement) {
        return UsageViewBundle.message("usage.target.exception");
      }
    }

    if (location instanceof UsageViewLongNameLocation) {
      if (element instanceof PsiPackage) {
        return ((PsiPackage)element).getQualifiedName();
      }
      else if (element instanceof PsiClass) {
        if (element instanceof PsiAnonymousClass) {
          return LangBundle.message("java.terms.anonymous.class");
        }
        else {
          String ret = ((PsiClass)element).getQualifiedName(); // It happens for local classes
          if (ret == null) {
            ret = ((PsiClass)element).getName();
          }
          return ret;
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
}

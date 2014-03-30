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
package com.intellij.psi.presentation.java;

import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JavaPresentationUtil {
  private JavaPresentationUtil() {
  }

  @NotNull
  public static ColoredItemPresentation getMethodPresentation(@NotNull final PsiMethod psiMethod) {
    return new ColoredItemPresentation() {
      @Override
      public String getPresentableText() {
        return PsiFormatUtil.formatMethod(
          psiMethod,
          PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
          PsiFormatUtilBase.SHOW_TYPE
        );
      }

      @Override
      public TextAttributesKey getTextAttributesKey() {
        if (psiMethod.isDeprecated()) {
          return CodeInsightColors.DEPRECATED_ATTRIBUTES;
        }
        return null;
      }

      @Override
      public String getLocationString() {
        return getJavaSymbolContainerText(psiMethod);
      }

      @Override
      public Icon getIcon(boolean open) {
        return psiMethod.getIcon(Iconable.ICON_FLAG_VISIBILITY);
      }
    };
  }

  @NotNull
  public static ItemPresentation getFieldPresentation(@NotNull final PsiField psiField) {
    return new ColoredItemPresentation() {
      @Override
      public String getPresentableText() {
        return psiField.getName();
      }

      @Override
      public TextAttributesKey getTextAttributesKey() {
        if (psiField.isDeprecated()) {
          return CodeInsightColors.DEPRECATED_ATTRIBUTES;
        }
        return null;
      }

      @Override
      public String getLocationString() {
        return getJavaSymbolContainerText(psiField);
      }

      @Override
      public Icon getIcon(boolean open) {
        return psiField.getIcon(Iconable.ICON_FLAG_VISIBILITY);
      }
    };
  }

  @Nullable
  private static String getJavaSymbolContainerText(@NotNull final PsiElement element) {
    final String result;
    PsiElement container = PsiTreeUtil.getParentOfType(element, PsiMember.class, PsiFile.class);

    if (container instanceof PsiClass) {
      String qName = ((PsiClass)container).getQualifiedName();
      if (qName != null) {
        result = qName;
      }
      else {
        result = ((PsiClass)container).getName();
      }
    }
    else if (container instanceof PsiJavaFile) {
      result = ((PsiJavaFile)container).getPackageName();
    }
    else {//TODO: local classes
      result = null;
    }
    if (result != null) {
      return PsiBundle.message("aux.context.display", result);
    }
    return null;
  }
}

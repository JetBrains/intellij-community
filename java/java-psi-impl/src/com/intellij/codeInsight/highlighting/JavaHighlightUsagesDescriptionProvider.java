// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.highlighting;

import com.intellij.core.JavaPsiBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NotNull;


public final class JavaHighlightUsagesDescriptionProvider implements ElementDescriptionProvider {
  private static final int METHOD_FLAGS = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS | PsiFormatUtilBase.SHOW_CONTAINING_CLASS;
  private static final int VARIABLE_FLAGS = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_CONTAINING_CLASS;

  @Override
  public String getElementDescription(@NotNull PsiElement element, @NotNull ElementDescriptionLocation location) {
    if (!(location instanceof HighlightUsagesDescriptionLocation)) return null;

    String elementName = null;
    if (element instanceof PsiClass) {
      elementName = ((PsiClass)element).getQualifiedName();
      if (elementName == null) {
        elementName = ((PsiClass)element).getName();
      }
    }
    else if (element instanceof PsiMethod) {
      elementName = PsiFormatUtil.formatMethod((PsiMethod)element, PsiSubstitutor.EMPTY, METHOD_FLAGS, PsiFormatUtilBase.SHOW_TYPE);
    }
    else if (element instanceof PsiVariable) {
      elementName = PsiFormatUtil.formatVariable((PsiVariable)element, VARIABLE_FLAGS, PsiSubstitutor.EMPTY);
    }
    else if (element instanceof PsiPackage) {
      elementName = ((PsiPackage)element).getQualifiedName();
    }
    return elementName == null ? null :
           JavaPsiBundle.message("element.kind.and.name", JavaElementKind.fromElement(element).subject(), elementName);
  }
}
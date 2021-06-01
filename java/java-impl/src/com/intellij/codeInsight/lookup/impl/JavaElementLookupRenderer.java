// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.beanProperties.BeanPropertyElement;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public class JavaElementLookupRenderer implements ElementLookupRenderer {
  @Override
  public boolean handlesItem(final Object element) {
    return element instanceof BeanPropertyElement;
  }

  @Override
  public void renderElement(final LookupItem item, final Object element, final LookupElementPresentation presentation) {
    presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(item));

    presentation.setItemText(PsiUtilCore.getName((PsiElement)element));
    presentation.setStrikeout(isToStrikeout(item));

    presentation.setTailText((String)item.getAttribute(LookupItem.TAIL_TEXT_ATTR), item.getAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR) != null);

    PsiType type = ((BeanPropertyElement)element).getPropertyType();
    presentation.setTypeText(type == null ? null : type.getPresentableText());
  }

  public static boolean isToStrikeout(LookupElement item) {
    final List<PsiMethod> allMethods = JavaCompletionUtil.getAllMethods(item);
    if (allMethods != null){
      for (PsiMethod method : allMethods) {
        if (!method.isValid()) { //?
          return false;
        }
        if (!isDeprecated(method)) {
          return false;
        }
      }
      return true;
    }
    return isDeprecated(item.getPsiElement());
  }

  public static boolean isDeprecated(@Nullable PsiElement element) {
    return element instanceof PsiDocCommentOwner && ((PsiDocCommentOwner)element).isDeprecated();
  }
}

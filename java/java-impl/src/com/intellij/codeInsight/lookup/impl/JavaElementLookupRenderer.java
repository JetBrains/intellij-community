/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.lookup.DefaultLookupItemRenderer;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.beanProperties.BeanPropertyElement;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class JavaElementLookupRenderer implements ElementLookupRenderer {
  @Override
  public boolean handlesItem(final Object element) {
    return element instanceof BeanPropertyElement;
  }

  @Override
  public void renderElement(final LookupItem item, final Object element, final LookupElementPresentation presentation) {
    presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(item, presentation.isReal()));

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

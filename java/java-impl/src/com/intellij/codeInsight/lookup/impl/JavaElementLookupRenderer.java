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
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
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
    return element instanceof PsiVariable ||
           element instanceof PsiKeyword || element instanceof PsiExpression ||
           element instanceof PsiTypeElement || element instanceof BeanPropertyElement;
  }

  @Override
  public void renderElement(final LookupItem item, final Object element, final LookupElementPresentation presentation) {
    presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(item, presentation.isReal()));

    presentation.setItemText(getName(element, item));
    presentation.setStrikeout(isToStrikeout(item));
    presentation.setItemTextBold(item.getAttribute(LookupItem.HIGHLIGHTED_ATTR) != null);

    presentation.setTailText((String)item.getAttribute(LookupItem.TAIL_TEXT_ATTR), item.getAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR) != null);

    presentation.setTypeText(getTypeText(element, item));
  }

  private static String getName(final Object o, final LookupItem<?> item) {
    final String presentableText = item.getPresentableText();
    if (presentableText != null) {
      return presentableText;
    }

    String name = "";
    if (o instanceof PsiElement) {
      final PsiElement element = (PsiElement)o;
      if (element.isValid()) {
        if (element instanceof PsiKeyword || element instanceof PsiExpression || element instanceof PsiTypeElement) {
          name = element.getText();
        } else {
          name = PsiUtilCore.getName(element);
        }
      }
    }
    else if (o instanceof PsiArrayType) {
      name = ((PsiArrayType)o).getDeepComponentType().getPresentableText();
    }
    else if (o instanceof PsiType) {
      name = ((PsiType)o).getPresentableText();
    }

    if (item.getAttribute(LookupItem.FORCE_QUALIFY) != null) {
      if (o instanceof PsiMember && ((PsiMember)o).getContainingClass() != null) {
        name = ((PsiMember)o).getContainingClass().getName() + "." + name;
      }
    }

    return StringUtil.notNullize(name);
  }

  @Nullable
  private static String getTypeText(final Object o, final LookupItem item) {
    String text = null;
    if (o instanceof PsiElement) {
      final PsiElement element = (PsiElement)o;
      if (element.isValid()) {
        if (element instanceof PsiVariable){
          PsiVariable variable = (PsiVariable)element;
          PsiType type = variable.getType();
          if (item instanceof VariableLookupItem) {
            type = ((VariableLookupItem)item).getSubstitutor().substitute(type);
          }
          text = type.getPresentableText();
        }
        else if (element instanceof PsiExpression){
          PsiExpression expression = (PsiExpression)element;
          PsiType type = expression.getType();
          if (type != null){
            text = type.getPresentableText();
          }
        }
        else if (element instanceof BeanPropertyElement) {
          return getTypeText(item, ((BeanPropertyElement)element).getPropertyType());
        }
      }
    }

    return text;
  }

  @Nullable
  private static String getTypeText(LookupItem item, @Nullable PsiType returnType) {
    if (returnType == null) {
      return null;
    }

    final PsiSubstitutor substitutor = (PsiSubstitutor)item.getAttribute(LookupItem.SUBSTITUTOR);
    if (substitutor != null) {
      return substitutor.substitute(returnType).getPresentableText();
    }
    return returnType.getPresentableText();
  }

  public static boolean isToStrikeout(LookupItem<?> item) {
    final List<PsiMethod> allMethods = item.getUserData(JavaCompletionUtil.ALL_METHODS_ATTRIBUTE);
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
    else if (item.getObject() instanceof PsiElement) {
      final PsiElement element = (PsiElement)item.getObject();
      if (element.isValid()) {
        return isDeprecated(element);
      }
    }
    return false;
  }

  private static boolean isDeprecated(PsiElement element) {
    return element instanceof PsiDocCommentOwner && ((PsiDocCommentOwner)element).isDeprecated();
  }
}

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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.beanProperties.BeanPropertyElement;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class JavaElementLookupRenderer implements ElementLookupRenderer {
  public boolean handlesItem(final Object element) {
    return element instanceof PsiClass || element instanceof PsiMember || element instanceof PsiVariable ||
           element instanceof PsiType || element instanceof PsiKeyword || element instanceof PsiExpression ||
           element instanceof PsiTypeElement || element instanceof BeanPropertyElement;
  }

  public void renderElement(final LookupItem item, final Object element, final LookupElementPresentation presentation) {
    presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(item, presentation.isReal()));

    final boolean bold = item.getAttribute(LookupItem.HIGHLIGHTED_ATTR) != null;
    boolean strikeout = isToStrikeout(item);
    presentation.setItemText(getName(element, item));
    presentation.setStrikeout(strikeout);
    presentation.setItemTextBold(bold);

    String tailText = StringUtil.notNullize(getTailText(element, item));
    boolean grayed = item.getAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR) != null;
    PsiSubstitutor substitutor = (PsiSubstitutor)item.getAttribute(LookupItem.SUBSTITUTOR);
    if (element instanceof PsiClass) {
      final PsiClass psiClass = (PsiClass)element;
      if (item.getAttribute(LookupItem.INDICATE_ANONYMOUS) != null &&
          (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT))) {
        tailText = "{...}" + tailText;
      }
      if (substitutor == null && psiClass.getTypeParameters().length > 0) {
        tailText = "<" + StringUtil.join(psiClass.getTypeParameters(), new Function<PsiTypeParameter, String>() {
          public String fun(PsiTypeParameter psiTypeParameter) {
            return psiTypeParameter.getName();
          }
        }, "," + (showSpaceAfterComma(psiClass) ? " " : "")) + ">" + tailText;
      }
      grayed = true;
    }
    presentation.setTailText(tailText, grayed);

    final String typeText = getTypeText(element, item);
    presentation.setTypeText(typeText != null ? typeText : "");
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
          name = PsiUtilBase.getName(element);
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

    PsiSubstitutor substitutor = (PsiSubstitutor)item.getAttribute(LookupItem.SUBSTITUTOR);
    if (o instanceof PsiClass && substitutor != null) {
      final PsiClass psiClass = (PsiClass)o;
      final PsiTypeParameter[] params = psiClass.getTypeParameters();
      if (params.length > 0) {
        return name + formatTypeParameters(substitutor, params);
      }

    }

    return StringUtil.notNullize(name);
  }

  @Nullable
  private static String getTailText(final Object o, final LookupItem item) {
    String text = null;
    if (o instanceof PsiElement) {
      final PsiElement element = (PsiElement)o;
      if (element.isValid() && element instanceof PsiMethod){
        PsiMethod method = (PsiMethod)element;
        final PsiSubstitutor substitutor = (PsiSubstitutor) item.getAttribute(LookupItem.SUBSTITUTOR);
        text = PsiFormatUtil.formatMethod(method,
                                          substitutor != null ? substitutor : PsiSubstitutor.EMPTY,
                                          PsiFormatUtil.SHOW_PARAMETERS,
                                          PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE);
      }
    }

    String tailText = (String)item.getAttribute(LookupItem.TAIL_TEXT_ATTR);
    if (tailText != null){
      if (text == null){
        text = tailText;
      }
      else{
        text += tailText;
      }
    }
    return text;
  }

  @Nullable
  private static String getTypeText(final Object o, final LookupItem item) {
    String text = null;
    if (o instanceof PsiElement) {
      final PsiElement element = (PsiElement)o;
      if (element.isValid()) {
        if (element instanceof PsiMethod){
          text = getTypeText(item, ((PsiMethod)element).getReturnType());
        }
        else if (element instanceof PsiVariable){
          PsiVariable variable = (PsiVariable)element;
          text = variable.getType().getPresentableText();
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

  @Nullable
  private static String formatTypeParameters(@NotNull final PsiSubstitutor substitutor, final PsiTypeParameter[] params) {
    final boolean space = showSpaceAfterComma(params[0]);
    StringBuilder buffer = new StringBuilder();
    buffer.append("<");
    for(int i = 0; i < params.length; i++){
      final PsiTypeParameter param = params[i];
      final PsiType type = substitutor.substitute(param);
      if(type == null){
        return "";
      }
      if (type instanceof PsiClassType && ((PsiClassType)type).getParameters().length > 0) {
        buffer.append(((PsiClassType)type).rawType().getPresentableText()).append("<...>");
      } else {
        buffer.append(type.getPresentableText());
      }

      if(i < params.length - 1) {
        buffer.append(",");
        if (space) {
          buffer.append(" ");
        }
      }
    }
    buffer.append(">");
    return buffer.toString();
  }

  private static boolean showSpaceAfterComma(PsiClass element) {
    return CodeStyleSettingsManager.getSettings(element.getProject()).SPACE_AFTER_COMMA;
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

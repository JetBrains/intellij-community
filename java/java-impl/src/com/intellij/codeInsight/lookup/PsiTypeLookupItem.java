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
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.DefaultInsertHandler;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.util.ClassConditionKey;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PsiTypeLookupItem extends LookupItem {
  public static final ClassConditionKey<PsiTypeLookupItem> CLASS_CONDITION_KEY = ClassConditionKey.create(PsiTypeLookupItem.class);
  private final boolean myDiamond;

  private PsiTypeLookupItem(Object o, @NotNull @NonNls String lookupString, boolean diamond) {
    super(o, lookupString);
    myDiamond = diamond;
  }

  @Override
  public boolean equals(final Object o) {
    return super.equals(o) && o instanceof PsiTypeLookupItem && getBracketsCount() == ((PsiTypeLookupItem) o).getBracketsCount();
  }

  @Override
  public void handleInsert(InsertionContext context) {
    context.getDocument().insertString(context.getTailOffset(), calcGenerics());
    DefaultInsertHandler.addImportForItem(context, this);

    int tail = context.getTailOffset();
    String braces = StringUtil.repeat("[]", getBracketsCount());
    Editor editor = context.getEditor();
    if (!braces.isEmpty()) {
      context.getDocument().insertString(tail, braces);
      editor.getCaretModel().moveToOffset(tail + 1);
    } else {
      editor.getCaretModel().moveToOffset(tail);
    }
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    InsertHandler handler = getInsertHandler();
    if (handler != null && !(handler instanceof DefaultInsertHandler)) {
      //noinspection unchecked
      handler.handleInsert(context, this);
    }
  }

  public String calcGenerics() {
    if (myDiamond) {
      return "<>";
    }

    if (getObject() instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)getObject();
      PsiSubstitutor substitutor = getSubstitutor();
      StringBuilder builder = new StringBuilder();
      for (PsiTypeParameter parameter : psiClass.getTypeParameters()) {
        PsiType substitute = substitutor.substitute(parameter);
        if (substitute == null || PsiUtil.resolveClassInType(substitute) == parameter) {
          return "";
        }
        if (builder.length() > 0) {
          builder.append(", ");
        }
        builder.append(substitute.getCanonicalText());
      }
      if (builder.length() > 0) {
        return "<" + builder + ">";
      }
    }
    return "";
  }

  @Override
  public int hashCode() {
    final int fromSuper = super.hashCode();
    final int dim = getBracketsCount();
    return fromSuper + dim * 31;
  }

  public int getBracketsCount() {
    final Integer integer = (Integer)getUserData(BRACKETS_COUNT_ATTR);
    return integer == null ? 0 : integer;
  }

  public static PsiTypeLookupItem createLookupItem(@NotNull PsiType type, @Nullable PsiElement context) {
    final PsiType original = type;
    int dim = 0;
    while (type instanceof PsiArrayType) {
      type = ((PsiArrayType)type).getComponentType();
      dim++;
    }

    PsiTypeLookupItem item = doCreateItem(type, context);

    if (dim > 0) {
      item.setAttribute(TAIL_TEXT_ATTR, " " + StringUtil.repeat("[]", dim));
      item.setAttribute(TAIL_TEXT_SMALL_ATTR, "");
      item.putUserData(BRACKETS_COUNT_ATTR, dim);
    }
    item.setAttribute(TYPE, original);
    return item;
  }

  private static PsiTypeLookupItem doCreateItem(final PsiType type, PsiElement context) {
    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult classResolveResult = ((PsiClassType)type).resolveGenerics();
      final PsiClass psiClass = classResolveResult.getElement();

      if (psiClass != null) {
        final PsiSubstitutor substitutor = classResolveResult.getSubstitutor();

        boolean diamond = false;
        if (type instanceof PsiClassReferenceType) {
          final PsiReferenceParameterList parameterList = ((PsiClassReferenceType)type).getReference().getParameterList();
          if (parameterList != null) {
            final PsiTypeElement[] typeParameterElements = parameterList.getTypeParameterElements();
            diamond = typeParameterElements.length == 1 && typeParameterElements[0].getType() instanceof PsiDiamondType;
          }
        }
        PsiClass resolved = JavaPsiFacade.getInstance(psiClass.getProject()).getResolveHelper().resolveReferencedClass(psiClass.getName(), context);
        String lookupString = psiClass.getName();
        if (!psiClass.getManager().areElementsEquivalent(resolved, psiClass)) {
          // inner class name should be shown qualified if its not accessible by single name
          PsiClass aClass = psiClass.getContainingClass();
          while (aClass != null) {
            lookupString = aClass.getName() + '.' + lookupString;
            aClass = aClass.getContainingClass();
          }
        }

        PsiTypeLookupItem item = new PsiTypeLookupItem(psiClass, lookupString, diamond);
        item.setAttribute(SUBSTITUTOR, substitutor);
        return item;
      }

    }
    return new PsiTypeLookupItem(type, type.getPresentableText(), false);
  }

  @NotNull
  private PsiSubstitutor getSubstitutor() {
    PsiSubstitutor attribute = (PsiSubstitutor)getAttribute(SUBSTITUTOR);
    return attribute != null ? attribute : PsiSubstitutor.EMPTY;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    final Object object = getObject();
    if (object instanceof PsiClass) {
      JavaPsiClassReferenceElement.renderClassItem(presentation, this, (PsiClass)object, myDiamond);
    } else {
      assert object instanceof PsiType;

      if (!(object instanceof PsiPrimitiveType)) {
        presentation.setIcon(DefaultLookupItemRenderer.getRawIcon(this, presentation.isReal()));
      }

      presentation.setItemText(((PsiType)object).getCanonicalText());
      presentation.setItemTextBold(getAttribute(LookupItem.HIGHLIGHTED_ATTR) != null || object instanceof PsiPrimitiveType);

      String tailText = (String)getAttribute(LookupItem.TAIL_TEXT_ATTR);
      if (tailText != null) {
        presentation.setTailText(tailText, getAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR) != null);
      }
    }
  }
}

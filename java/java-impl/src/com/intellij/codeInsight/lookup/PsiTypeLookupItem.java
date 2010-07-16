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
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class PsiTypeLookupItem extends LookupItem {
  public PsiTypeLookupItem(Object o, @NotNull @NonNls String lookupString) {
    super(o, lookupString);
  }

  @Override
  public boolean equals(final Object o) {
    return super.equals(o) && o instanceof PsiTypeLookupItem && getBracketsCount() == ((PsiTypeLookupItem) o).getBracketsCount();
  }

  @Override
  public void handleInsert(InsertionContext context) {
    DefaultInsertHandler.addImportForItem(context.getFile(), context.getStartOffset(), this);
    super.handleInsert(context);
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

  public static LookupItem createLookupItem(@NotNull PsiType type, @Nullable PsiElement context) {
    final PsiType original = type;
    int dim = 0;
    while (type instanceof PsiArrayType) {
      type = ((PsiArrayType)type).getComponentType();
      dim++;
    }

    LookupItem item = doCreateItem(type, context);

    if (dim > 0) {
      final StringBuilder tail = new StringBuilder();
      for (int i = 0; i < dim; i++) {
        tail.append("[]");
      }
      item.setAttribute(TAIL_TEXT_ATTR, " " + tail.toString());
      item.setAttribute(TAIL_TEXT_SMALL_ATTR, "");
      item.putUserData(BRACKETS_COUNT_ATTR, dim);
    }
    item.setAttribute(TYPE, original);
    return item;
  }

  private static LookupItem doCreateItem(PsiType type, PsiElement context) {
    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult classResolveResult = ((PsiClassType)type).resolveGenerics();
      final PsiClass psiClass = classResolveResult.getElement();
      final PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
      final String text = type.getCanonicalText();
      String typeString = text;
      String typeParams = "";
      if (text.indexOf('<') > 0 && text.endsWith(">")) {
        typeString = text.substring(0, text.indexOf('<'));
        typeParams = text.substring(text.indexOf('<'));
      }

      String lookupString = text.substring(typeString.lastIndexOf('.') + 1);
      if (psiClass != null) {
        PsiClass resolved =
          JavaPsiFacade.getInstance(psiClass.getProject()).getResolveHelper().resolveReferencedClass(psiClass.getName(), context);
        if (!psiClass.getManager().areElementsEquivalent(resolved, psiClass)) {
          // inner class name should be shown qualified if its not accessible by single name
          PsiClass aClass = psiClass;
          lookupString = "";
          while (aClass != null) {
            lookupString = aClass.getName() + (lookupString == "" ? "" : ".") + lookupString;
            aClass = aClass.getContainingClass();
          }
          lookupString += typeParams;
        }
        LookupItem item = new PsiTypeLookupItem(psiClass, lookupString);
        item.setAttribute(SUBSTITUTOR, substitutor);
        return item;
      }

    }
    return new LookupItem(type, type.getPresentableText());
  }
  
  public static @Nullable PsiTypeLookupItem from(LookupElement element) {
    if (element instanceof PsiTypeLookupItem) return (PsiTypeLookupItem)element;
    else if (element instanceof LookupElementDecorator) {
      element = ((LookupElementDecorator)element).getDelegate();
      if (element instanceof PsiTypeLookupItem) return (PsiTypeLookupItem)element;
    }
    
    return null;
  }
}

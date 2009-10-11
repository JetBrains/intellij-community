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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import com.intellij.psi.*;

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
  public int hashCode() {
    final int fromSuper = super.hashCode();
    final int dim = getBracketsCount();
    return fromSuper + dim * 31;
  }

  public int getBracketsCount() {
    final Integer integer = (Integer)getUserData(LookupItem.BRACKETS_COUNT_ATTR);
    return integer == null ? 0 : integer;
  }

  public static LookupItem createLookupItem(PsiType type) {
    final PsiType original = type;
    int dim = 0;
    while (type instanceof PsiArrayType) {
      type = ((PsiArrayType)type).getComponentType();
      dim++;
    }

    LookupItem item;
    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult classResolveResult = ((PsiClassType)type).resolveGenerics();
      final PsiClass psiClass = classResolveResult.getElement();
      final PsiSubstitutor substitutor = classResolveResult.getSubstitutor();
      final String text = type.getCanonicalText();
      String typeString = text;
      if (text.indexOf('<') > 0 && text.endsWith(">")) {
        typeString = text.substring(0, text.indexOf('<'));
      }
      String s = text.substring(typeString.lastIndexOf('.') + 1);
      item = psiClass != null ? new PsiTypeLookupItem(psiClass, s) : new PsiTypeLookupItem(text, s);
      item.setAttribute(LookupItem.SUBSTITUTOR, substitutor);
    }
    else {
      item = new LookupItem(type, type.getPresentableText());
    }

    if (dim > 0) {
      final StringBuilder tail = new StringBuilder();
      for (int i = 0; i < dim; i++) {
        tail.append("[]");
      }
      item.setAttribute(LookupItem.TAIL_TEXT_ATTR, " " + tail.toString());
      item.setAttribute(LookupItem.TAIL_TEXT_SMALL_ATTR, "");
      item.putUserData(LookupItem.BRACKETS_COUNT_ATTR, dim);
    }
    item.setAttribute(LookupItem.TYPE, original);
    return item;
  }
}

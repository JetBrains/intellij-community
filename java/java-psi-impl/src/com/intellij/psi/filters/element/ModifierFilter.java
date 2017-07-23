/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.filters.element;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ModifierFilter extends ClassFilter {
  public final List<ModifierRestriction> myModifierRestrictions;

  public ModifierFilter(@PsiModifier.ModifierConstant String modifier, boolean hasToBe) {
    this(Collections.singletonList(new ModifierRestriction(modifier, hasToBe)));
  }

  public ModifierFilter(String... modifiers) {
    this(ContainerUtil.map(modifiers, modifier -> new ModifierRestriction(modifier, true)));
  }

  private ModifierFilter(List<ModifierRestriction> restrictions) {
    super(PsiModifierListOwner.class);
    myModifierRestrictions = restrictions;
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context) {
    if (!(element instanceof PsiModifierListOwner)) return false;

    PsiModifierList list = ((PsiModifierListOwner)element).getModifierList();
    if (list != null) {
      for (ModifierRestriction psiModifier : myModifierRestrictions) {
        boolean shouldHave = psiModifier.isSet;
        if (shouldHave != list.hasModifierProperty(psiModifier.modifierName)) {
          return false;
        }
      }
    }

    return true;
  }

  private static final class ModifierRestriction {
    public final String modifierName;
    public final boolean isSet;

    ModifierRestriction(String modifierName, boolean isSet) {
      this.modifierName = modifierName;
      this.isSet = isSet;
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("modifiers(");
    Iterator<ModifierRestriction> iter = myModifierRestrictions.iterator();
    while (iter.hasNext()) {
      ModifierRestriction rest = iter.next();
      sb.append(rest.modifierName).append("=").append(rest.isSet);
      if (iter.hasNext()) {
        sb.append(", ");
      }
    }
    sb.append(")");
    return sb.toString();
  }
}
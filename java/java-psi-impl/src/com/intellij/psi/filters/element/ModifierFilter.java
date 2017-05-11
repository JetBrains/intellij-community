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
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ModifierFilter extends ClassFilter{
  public final List<ModifierRestriction> myModifierRestrictions = new ArrayList<>();

  private ModifierFilter(){
    super(PsiModifierListOwner.class);
  }

  public ModifierFilter(@PsiModifier.ModifierConstant String modifier, boolean hasToBe){
    this();
    addModifierRestriction(modifier, hasToBe);
  }

  public ModifierFilter(String... modifiers){
    this();
    for (@PsiModifier.ModifierConstant String modifier : modifiers) {
      addModifierRestriction(modifier, true);
    }
  }

  private void addModifierRestriction(@PsiModifier.ModifierConstant String mod, boolean hasToBe){
    myModifierRestrictions.add(new ModifierRestriction(mod, hasToBe));
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context){
    if(element instanceof PsiModifierListOwner){
      final PsiModifierList list = ((PsiModifierListOwner)element).getModifierList();
      if(list == null) return true;
      for (final ModifierRestriction psiModifier : myModifierRestrictions) {
        boolean shouldHave = psiModifier.myIsSet;
        if (shouldHave != list.hasModifierProperty(psiModifier.myModifierName)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  protected static final class ModifierRestriction{
    @PsiModifier.ModifierConstant public final String myModifierName;
    public final boolean myIsSet;

    ModifierRestriction(@PsiModifier.ModifierConstant String modifierName, boolean isSet){
      myModifierName = modifierName;
      myIsSet = isSet;
    }
  }

  public String toString(){
    @NonNls StringBuilder sb = new StringBuilder("modifiers(");
    Iterator<ModifierRestriction> iter = myModifierRestrictions.iterator();
    while(iter.hasNext()){
      final ModifierRestriction rest = iter.next();
      sb.append(rest.myModifierName).append("=").append(rest.myIsSet);
      if(iter.hasNext()){
        sb.append(", ");
      }
    }
    sb.append(")");
    return sb.toString();
  }
}

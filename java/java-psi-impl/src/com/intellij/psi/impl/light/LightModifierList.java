/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class LightModifierList extends LightElement implements PsiModifierList {
  private final Set<String> myModifiers;

  public LightModifierList(PsiModifierListOwner modifierListOwner) {
    this(modifierListOwner.getManager());
    copyModifiers(modifierListOwner.getModifierList());
  }

  public LightModifierList(PsiManager manager) {
    this(manager, JavaLanguage.INSTANCE);
  }

  public LightModifierList(PsiManager manager, final Language language, String... modifiers) {
    super(manager, language);
    myModifiers = ContainerUtil.newTroveSet(modifiers);
  }

  public void addModifier(String modifier) {
    myModifiers.add(modifier);
  }

  public void copyModifiers(PsiModifierList modifierList) {
    if (modifierList == null) return;
    for (String modifier : PsiModifier.MODIFIERS) {
      if (modifierList.hasExplicitModifier(modifier)) {
        addModifier(modifier);
      }
    }
  }

  public void clearModifiers() {
    myModifiers.clear();
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return myModifiers.contains(name);
  }

  @Override
  public boolean hasExplicitModifier(@NotNull String name) {
    return myModifiers.contains(name);
  }

  @Override
  public void setModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void checkSetModifierProperty(@NotNull String name, boolean value) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  @NotNull
  public PsiAnnotation[] getAnnotations() {
    //todo
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    return getAnnotations();
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    return null;
  }

  @Override
  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    throw new IncorrectOperationException();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitModifierList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiModifierList";
  }

  @Override
  public String getText() {
    StringBuilder buffer = new StringBuilder();

    for (String modifier : PsiModifier.MODIFIERS) {
      if (hasExplicitModifier(modifier)) {
        buffer.append(modifier);
        buffer.append(' ');
      }
    }

    if (buffer.length() > 0) {
      buffer.delete(buffer.length() - 1, buffer.length());
    }
    return buffer.toString();
  }

  public String[] getModifiers() {
    return ArrayUtil.toStringArray(myModifiers);
  }
}

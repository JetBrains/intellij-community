// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class LightModifierList extends LightElement implements PsiModifierList {
  private final Set<String> myModifiers;

  public LightModifierList(@NotNull PsiModifierListOwner modifierListOwner) {
    this(modifierListOwner.getManager());
    copyModifiers(modifierListOwner.getModifierList());
  }

  public LightModifierList(PsiManager manager) {
    this(manager, JavaLanguage.INSTANCE);
  }

  public LightModifierList(PsiManager manager, Language language, String... modifiers) {
    super(manager, language);
    myModifiers = ContainerUtil.newHashSet(modifiers);
  }

  public void addModifier(@NotNull String modifier) {
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
  public PsiAnnotation @NotNull [] getAnnotations() {
    //todo
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  public PsiAnnotation @NotNull [] getApplicableAnnotations() {
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

  @Override
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

  public String @NotNull [] getModifiers() {
    return ArrayUtilRt.toStringArray(myModifiers);
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PsiCompositeModifierList extends LightModifierList {
  private final List<? extends PsiModifierList> mySublists;

  public PsiCompositeModifierList(final PsiManager manager, List<? extends PsiModifierList> sublists) {
    super(manager);
    mySublists = sublists;
  }

  @Override
  public PsiAnnotation @NotNull [] getAnnotations() {
    List<PsiAnnotation> annotations = new ArrayList<>();
    for (PsiModifierList list : mySublists) {
      ContainerUtil.addAll(annotations, list.getAnnotations());
    }
    return annotations.toArray(PsiAnnotation.EMPTY_ARRAY);
  }

  @Override
  public PsiAnnotation findAnnotation(final @NotNull String qualifiedName) {
    for (PsiModifierList sublist : mySublists) {
      final PsiAnnotation annotation = sublist.findAnnotation(qualifiedName);
      if (annotation != null) return annotation;
    }

    return null;
  }

  @Override
  public boolean hasModifierProperty(final @NotNull String name) {
    for (PsiModifierList sublist : mySublists) {
      if (sublist.hasModifierProperty(name)) return true;
    }
    return false;
  }

  @Override
  public boolean hasExplicitModifier(final @NotNull String name) {
    for (PsiModifierList sublist : mySublists) {
      if (sublist.hasExplicitModifier(name)) return true;
    }
    return false;
  }
}

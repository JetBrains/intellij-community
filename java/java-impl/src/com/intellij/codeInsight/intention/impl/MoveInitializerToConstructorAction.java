// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddDefaultConstructorFix;
import com.intellij.java.JavaBundle;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class MoveInitializerToConstructorAction extends BaseMoveInitializerToMethodAction {
  @Override
  @NotNull
  public String getText() {
    return JavaBundle.message("intention.move.initializer.to.constructor");
  }

  @Override
  protected boolean isAvailable(@NotNull PsiField field) {
    if (!super.isAvailable(field)) return false;
    PsiClass containingClass = field.getContainingClass();
    assert containingClass != null;
    PsiMethod[] constructors = containingClass.getConstructors();
    if (constructors.length > 0 && ContainerUtil.all(constructors, c -> c instanceof SyntheticElement)) return false;
    if (field.hasModifierProperty(PsiModifier.FINAL)) {
      PsiClassInitializer[] initializers = containingClass.getInitializers();
      PsiElement[] elements =
        Arrays.stream(containingClass.getFields())
        .map(f -> f.getInitializer())
        .filter(Objects::nonNull)
        .toArray(PsiElement[]::new);
      return ReferencesSearch.search(field, new LocalSearchScope(ArrayUtil.mergeArrays(elements, initializers))).findFirst() == null;
    }
    return true;
  }

  @NotNull
  @Override
  protected Collection<String> getUnsuitableModifiers() {
    return Collections.singletonList(PsiModifier.STATIC);
  }

  @NotNull
  @Override
  protected Collection<PsiMethod> getOrCreateMethods(@NotNull PsiClass aClass) {
    final Collection<PsiMethod> constructors = Arrays.asList(aClass.getConstructors());
    if (constructors.isEmpty()) {
      return createConstructor(aClass);
    }

    return removeChainedConstructors(constructors);
  }

  @NotNull
  private static Collection<PsiMethod> removeChainedConstructors(@NotNull Collection<? extends PsiMethod> constructors) {
    final List<PsiMethod> result = new ArrayList<>(constructors);
    result.removeIf(constructor -> !JavaHighlightUtil.getChainedConstructors(constructor).isEmpty());
    return result;
  }

  @NotNull
  private static Collection<PsiMethod> createConstructor(@NotNull PsiClass aClass) {
    AddDefaultConstructorFix.addDefaultConstructor(aClass);
    return Arrays.asList(aClass.getConstructors());
  }
}
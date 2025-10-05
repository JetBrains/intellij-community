// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.impl.source;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaDummyHolderFactory implements HolderFactory {
  @Override
  public @NotNull DummyHolder createHolder(@NotNull PsiManager manager, @NotNull TreeElement contentElement, @Nullable PsiElement context) {
    return new JavaDummyHolder(manager, contentElement, context);
  }

  @Override
  public @NotNull DummyHolder createHolder(@NotNull PsiManager manager,
                                           @Nullable TreeElement contentElement, @Nullable PsiElement context, @Nullable CharTable table) {
    return new JavaDummyHolder(manager, contentElement, context, table);
  }

  @Override
  public @NotNull DummyHolder createHolder(@NotNull PsiManager manager, @Nullable PsiElement context) {
    return new JavaDummyHolder(manager, context);
  }

  @Override
  public @NotNull DummyHolder createHolder(@NotNull PsiManager manager, @NotNull Language language, @Nullable PsiElement context) {
    return language == JavaLanguage.INSTANCE ? new JavaDummyHolder(manager, context) : new DummyHolder(manager, language, context);
  }

  @Override
  public @NotNull DummyHolder createHolder(@NotNull PsiManager manager, @Nullable PsiElement context, @Nullable CharTable table) {
    return new JavaDummyHolder(manager, context, table);
  }

  @Override
  public @NotNull DummyHolder createHolder(@NotNull PsiManager manager, @Nullable CharTable table, @NotNull Language language) {
    return new JavaDummyHolder(manager, table);
  }

  @Override
  public @NotNull DummyHolder createHolder(@NotNull PsiManager manager, @Nullable CharTable table, boolean validity) {
    return new JavaDummyHolder(manager, table, validity);
  }
}
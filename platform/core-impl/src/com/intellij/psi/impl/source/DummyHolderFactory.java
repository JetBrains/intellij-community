// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public final class DummyHolderFactory  {
  private static HolderFactory INSTANCE = new DefaultFactory();

  private DummyHolderFactory() {}

  public static void setFactory(HolderFactory factory) {
    INSTANCE = factory;
  }

  public static @NotNull DummyHolder createHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context) {
    return INSTANCE.createHolder(manager, contentElement, context);
  }

  public static @NotNull DummyHolder createHolder(@NotNull PsiManager manager, CharTable table, boolean validity) {
    return INSTANCE.createHolder(manager, table, validity);
  }

  public static @NotNull DummyHolder createHolder(@NotNull PsiManager manager, PsiElement context) {
    return INSTANCE.createHolder(manager, context);
  }

  public static @NotNull DummyHolder createHolder(@NotNull PsiManager manager, Language language, PsiElement context) {
    return INSTANCE.createHolder(manager, language, context);
  }

  public static @NotNull DummyHolder createHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context, CharTable table) {
    return INSTANCE.createHolder(manager, contentElement, context, table);
  }

  public static @NotNull DummyHolder createHolder(@NotNull PsiManager manager, PsiElement context, CharTable table) {
    return INSTANCE.createHolder(manager, context, table);
  }

  public static @NotNull DummyHolder createHolder(@NotNull PsiManager manager, CharTable table, Language language) {
    return INSTANCE.createHolder(manager, table, language);
  }

  private static class DefaultFactory implements HolderFactory {
    @Override
    public @NotNull DummyHolder createHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context) {
      return new DummyHolder(manager, contentElement, context);
    }

    @Override
    public @NotNull DummyHolder createHolder(@NotNull PsiManager manager, CharTable table, boolean validity) {
      return new DummyHolder(manager, table, validity);
    }

    @Override
    public @NotNull DummyHolder createHolder(@NotNull PsiManager manager, PsiElement context) {
      return new DummyHolder(manager, context);
    }

    @Override
    public @NotNull DummyHolder createHolder(@NotNull PsiManager manager, Language language, PsiElement context) {
      return new DummyHolder(manager, language, context);
    }

    @Override
    public @NotNull DummyHolder createHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context, CharTable table) {
      return new DummyHolder(manager, contentElement, context, table);
    }

    @Override
    public @NotNull DummyHolder createHolder(@NotNull PsiManager manager, PsiElement context, CharTable table) {
      return new DummyHolder(manager, context, table);
    }

    @Override
    public @NotNull DummyHolder createHolder(@NotNull PsiManager manager, CharTable table, Language language) {
      return new DummyHolder(manager, table, language);
    }
  }
}
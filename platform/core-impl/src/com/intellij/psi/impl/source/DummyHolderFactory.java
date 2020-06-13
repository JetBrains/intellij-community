// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public static DummyHolder createHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context) {
    return INSTANCE.createHolder(manager, contentElement, context);
  }

  @NotNull
  public static DummyHolder createHolder(@NotNull PsiManager manager, CharTable table, boolean validity) {
    return INSTANCE.createHolder(manager, table, validity);
  }

  @NotNull
  public static DummyHolder createHolder(@NotNull PsiManager manager, PsiElement context) {
    return INSTANCE.createHolder(manager, context);
  }

  @NotNull
  public static DummyHolder createHolder(@NotNull PsiManager manager, Language language, PsiElement context) {
    return INSTANCE.createHolder(manager, language, context);
  }

  @NotNull
  public static DummyHolder createHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context, CharTable table) {
    return INSTANCE.createHolder(manager, contentElement, context, table);
  }

  @NotNull
  public static DummyHolder createHolder(@NotNull PsiManager manager, PsiElement context, CharTable table) {
    return INSTANCE.createHolder(manager, context, table);
  }

  @NotNull
  public static DummyHolder createHolder(@NotNull PsiManager manager, final CharTable table, final Language language) {
    return INSTANCE.createHolder(manager, table, language);
  }

  private static class DefaultFactory implements HolderFactory {
    @NotNull
    @Override
    public DummyHolder createHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context) {
      return new DummyHolder(manager, contentElement, context);
    }

    @NotNull
    @Override
    public DummyHolder createHolder(@NotNull PsiManager manager, CharTable table, boolean validity) {
      return new DummyHolder(manager, table, validity);
    }

    @NotNull
    @Override
    public DummyHolder createHolder(@NotNull PsiManager manager, PsiElement context) {
      return new DummyHolder(manager, context);
    }

    @NotNull
    @Override
    public DummyHolder createHolder(@NotNull final PsiManager manager, final Language language, final PsiElement context) {
      return new DummyHolder(manager, language, context);
    }

    @NotNull
    @Override
    public DummyHolder createHolder(@NotNull PsiManager manager, TreeElement contentElement, PsiElement context, CharTable table) {
      return new DummyHolder(manager, contentElement, context, table);
    }

    @NotNull
    @Override
    public DummyHolder createHolder(@NotNull PsiManager manager, PsiElement context, CharTable table) {
      return new DummyHolder(manager, context, table);
    }

    @NotNull
    @Override
    public DummyHolder createHolder(@NotNull PsiManager manager, final CharTable table, final Language language) {
      return new DummyHolder(manager, table, language);
    }
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.psi.PsiElement;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A single inspection, together with the scopes in which it is active. */
public interface Tools {
  @NotNull
  @RequiresReadLock
  InspectionToolWrapper<?, ?> getInspectionTool(@Nullable PsiElement element);

  @NotNull
  String getShortName();

  @NotNull
  InspectionToolWrapper<?, ?> getTool();

  @NotNull
  List<ScopeToolState> getTools();

  void collectTools(@NotNull List<? super ScopeToolState> result);

  @NotNull
  ScopeToolState getDefaultState();

  boolean isEnabled();

  boolean isEnabled(@Nullable PsiElement element);

  @Nullable
  InspectionToolWrapper<?, ?> getEnabledTool(@Nullable PsiElement element);

  default @Nullable InspectionToolWrapper<?, ?> getEnabledTool(@Nullable PsiElement element, boolean includeDoNotShow) {
    return getEnabledTool(element);
  }
}
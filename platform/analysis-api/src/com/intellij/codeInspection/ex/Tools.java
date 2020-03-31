// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface Tools {
  @NotNull
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

  @Nullable
  default InspectionToolWrapper<?, ?> getEnabledTool(@Nullable PsiElement element, boolean includeDoNotShow) {
    return getEnabledTool(element);
  }
}
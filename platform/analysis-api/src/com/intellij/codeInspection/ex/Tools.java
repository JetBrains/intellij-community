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

  /**
   * Checks if the inspection tool is enabled.
   * <p>
   * If this method returns {@code false}, the tool is considered
   * disabled for all files and scopes, and {@link #isEnabled(PsiElement)} will also return
   * {@code false} for any element.
   *
   * @return {@code true} if the tool is enabled, {@code false} otherwise.
   */
  boolean isEnabled();

  /**
   * Checks if the inspection tool is enabled for the specific {@link PsiElement}.
   * <p>
   * This method correctly considers the provided {@link PsiElement element}'s context
   * and the overall profile configuration, including any custom scopes
   * ({@code Settings | Appearance & Behavior | Scopes}) where the inspection might be specifically enabled or disabled.
   *
   * @return {@code true} if the tool is enabled for the given element, {@code false} otherwise.
   */
  boolean isEnabled(@Nullable PsiElement element);

  @Nullable
  InspectionToolWrapper<?, ?> getEnabledTool(@Nullable PsiElement element);

  default @Nullable InspectionToolWrapper<?, ?> getEnabledTool(@Nullable PsiElement element, boolean includeDoNotShow) {
    return getEnabledTool(element);
  }
}
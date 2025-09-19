// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class CompletionPreselectionBehaviourProvider {
  private static final ExtensionPointName<CompletionPreselectionBehaviourProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.completion.preselectionBehaviourProvider");

  public boolean shouldPreselectFirstSuggestion(@NotNull CompletionParameters parameters) {
    return true;
  }

  @ApiStatus.Internal
  public static @NotNull List<CompletionPreselectionBehaviourProvider> getExtensions() {
    return EP_NAME.getExtensionList();
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CompletionLocation implements UserDataHolder {
  private final CompletionParameters myCompletionParameters;
  private final ProcessingContext myProcessingContext = new ProcessingContext();

  public CompletionLocation(@NotNull CompletionParameters completionParameters) {
    myCompletionParameters = completionParameters;
  }

  public @NotNull CompletionParameters getCompletionParameters() {
    return myCompletionParameters;
  }

  public @NotNull CompletionType getCompletionType() {
    return myCompletionParameters.getCompletionType();
  }

  public @NotNull Project getProject() {
    return myCompletionParameters.getPosition().getProject();
  }

  public @NotNull ProcessingContext getProcessingContext() {
    return myProcessingContext;
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return myProcessingContext.get(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myProcessingContext.put(key, value);
  }
}

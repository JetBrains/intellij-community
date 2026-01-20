// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CompletionLocation implements UserDataHolder {
  private final BaseCompletionParameters myCompletionParameters;
  private final ProcessingContext myProcessingContext = new ProcessingContext();

  public CompletionLocation(@NotNull BaseCompletionParameters completionParameters) {
    myCompletionParameters = completionParameters;
  }

  public CompletionLocation(@NotNull CompletionParameters completionParameters) {
    myCompletionParameters = completionParameters;
  }

  /**
   * Consider using {@link #getBaseCompletionParameters()} instead, if you don't need things like {@link com.intellij.openapi.editor.Editor}
   * 
   * @return completion parameters
   * 
   * @throws ClassCastException if completion parameters are not instance of {@link CompletionParameters}, which may happen 
   * if the {@linkplain com.intellij.modcompletion.ModCompletionItemProvider mod command completion} works. 
   */
  @ApiStatus.Obsolete
  public @NotNull CompletionParameters getCompletionParameters() {
    return (CompletionParameters)myCompletionParameters;
  }

  /**
   * @return base completion parameters
   */
  public @NotNull BaseCompletionParameters getBaseCompletionParameters() {
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

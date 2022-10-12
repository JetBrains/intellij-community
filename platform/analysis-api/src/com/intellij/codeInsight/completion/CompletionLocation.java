// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompletionLocation implements UserDataHolder {
  private final CompletionParameters myCompletionParameters;
  private final ProcessingContext myProcessingContext = new ProcessingContext();

  public CompletionLocation(final CompletionParameters completionParameters) {
    myCompletionParameters = completionParameters;
  }

  public CompletionParameters getCompletionParameters() {
    return myCompletionParameters;
  }

  public CompletionType getCompletionType() {
    return myCompletionParameters.getCompletionType();
  }

  public Project getProject() {
    return myCompletionParameters.getPosition().getProject();
  }

  public ProcessingContext getProcessingContext() {
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

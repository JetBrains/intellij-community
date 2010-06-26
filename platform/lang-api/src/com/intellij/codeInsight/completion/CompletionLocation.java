/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
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

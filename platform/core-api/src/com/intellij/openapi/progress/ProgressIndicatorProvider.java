/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.progress;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class ProgressIndicatorProvider {
  @Nullable
  public static ProgressIndicatorProvider ourInstance;

  @Nullable
  public static ProgressIndicatorProvider getInstance() {
    return ourInstance;
  }

  public abstract ProgressIndicator getProgressIndicator();

  protected abstract void doCheckCanceled() throws ProcessCanceledException;

  @Nullable
  public static ProgressIndicator getGlobalProgressIndicator() {
    return ourInstance != null ? ourInstance.getProgressIndicator() : null;
  }

  public abstract NonCancelableSection startNonCancelableSection();

  @NotNull
  public static NonCancelableSection startNonCancelableSectionIfSupported() {
    return ourInstance != null ? ourInstance.startNonCancelableSection() : NonCancelableSection.EMPTY;
  }

  public static volatile boolean ourNeedToCheckCancel = false;
  public static void checkCanceled() throws ProcessCanceledException {
    // smart optimization! There's a thread started in ProgressManagerImpl, that set's this flag up once in 10 milliseconds
    if (ourNeedToCheckCancel && ourInstance != null) {
      ourInstance.doCheckCanceled();
      ourNeedToCheckCancel = false;
    }
  }
}

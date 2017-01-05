/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
  public static ProgressIndicatorProvider getInstance() {
    return ProgressManager.getInstance();
  }

  public abstract ProgressIndicator getProgressIndicator();

  protected abstract void doCheckCanceled() throws ProcessCanceledException;

  @Nullable
  public static ProgressIndicator getGlobalProgressIndicator() {
    return getInstance().getProgressIndicator();
  }

  @NotNull
  @Deprecated // use ProgressManager.executeNonCancelableSection() instead
  public abstract NonCancelableSection startNonCancelableSection();

  @NotNull
  @Deprecated // use ProgressManager.executeNonCancelableSection() instead
  public static NonCancelableSection startNonCancelableSectionIfSupported() {
    return getInstance().startNonCancelableSection();
  }

  public static void checkCanceled() throws ProcessCanceledException {
    ProgressManager.checkCanceled();
  }
}

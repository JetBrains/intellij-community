/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.progress.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * An interface that can be implemented by the ProgressIndicator to be called from CheckCanceledHook interface. 
 */
public interface PingProgress extends CoreProgressManager.CheckCanceledHook {
  void interact();

  /**
   * When on UI thread under a PingProgress, invoke its {@link #interact()}. This might, for example, repaint the progress
   * to give the user feedback that the IDE is working on a long-running operation and not frozen.
   */
  static void interactWithEdtProgress() {
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (indicator instanceof PingProgress && EDT.isCurrentThreadEdt()) {
      ((PingProgress)indicator).interact();
    }
  }

  @ApiStatus.Internal
  @Override
  default boolean runHook(@Nullable ProgressIndicator indicator) {
    interact();
    return false;
  }
}

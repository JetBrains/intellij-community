// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress;

import org.jetbrains.annotations.NotNull;

public final class RunBackgroundable {
  private RunBackgroundable() {
  }

  /**
   * @deprecated use {@link ProgressManager#run(Task)}
   */
  @Deprecated
  public static void run(@NotNull final Task task) {
    ProgressManager.getInstance().run(task);
  }
}

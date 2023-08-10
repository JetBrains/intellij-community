// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface ExpirableRunnable extends Runnable, Expirable  {
  static ExpirableRunnable forProject(@NotNull Project project, @NotNull Runnable runnable) {
    return new ExpirableRunnable() {
      @Override
      public boolean isExpired() {
        return project.isDisposed();
      }

      @Override
      public void run() {
        runnable.run();
      }
    };
  }
}

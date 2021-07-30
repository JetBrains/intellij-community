// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public interface TargetProgressIndicator {
  TargetProgressIndicator EMPTY = new TargetProgressIndicator() {
    @Override
    public void addText(@Nls @NotNull String text, @NotNull Key<?> key) { }

    @Override
    public boolean isCanceled() {
      return false;
    }

    @Override
    public void stop() { }

    @Override
    public boolean isStopped() {
      return false;
    }
  };

  void addText(@Nls @NotNull String text, @NotNull Key<?> key);

  default void addSystemLine(@Nls @NotNull String message) {
    addText(message + "\n", ProcessOutputType.SYSTEM);
  }

  boolean isCanceled();

  void stop();

  boolean isStopped();

  default void stopWithErrorMessage(@NlsContexts.DialogMessage @NotNull String text) {
    addText(text + "\n", ProcessOutputType.STDERR);
    stop();
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.bootRuntime.command;

import com.intellij.bootRuntime.bundles.Runtime;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Consumer;

public abstract class Command extends AbstractAction {
  protected final Runtime runtime;

  Command(String name, Runtime runtime) {
    super(name);
    this.runtime = runtime;
  }

  protected Runtime getRuntime() {
    return runtime;
  }

  protected void runWithProgress(String title, final Consumer<ProgressIndicator> progressIndicatorConsumer) {
    ProgressManager.getInstance().run(new Task.Modal(null, title, false) {
      @Override
      public void run(@NotNull ProgressIndicator progressIndicator) {
        progressIndicatorConsumer.accept(progressIndicator);
      }
    });
  }
}

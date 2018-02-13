// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.update;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RunningApplicationUpdaterProvider {
  ExtensionPointName<RunningApplicationUpdaterProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.runningApplicationUpdaterProvider");

  @Nullable
  RunningApplicationUpdater createUpdater(@NotNull Project project, @NotNull ProcessHandler process);
}

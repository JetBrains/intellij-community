// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@ApiStatus.Internal
public interface FreezeProfiler {
  /** @param reportDir directory to collect some intermediate profiling info */
  void start(@NotNull Path reportDir);

  void stop();

  /** @param reportDir directory for intermediate results -- same as passed in {@link #start(File)} before */
  @NotNull List<Attachment> getAttachments(@NotNull Path reportDir);

  default void checkCrash(@NotNull String crashContent) {}
}

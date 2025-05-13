// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.NodeSourcePathMapper;

import java.nio.file.Path;

public interface BuildContext extends DiagnosticSink {
  String getTargetName();

  boolean isRebuild();

  boolean isCanceled();

  /**
   * @return the BazelWorker working dir (can be a sandbox dir)
   * Source and library inputs should be resolved against the base dir
   */
  @NotNull
  Path getBaseDir();

  /**
   * @return base directory where incremental data storages are to be stored
   */
  @NotNull
  Path getDataDir();

  @NotNull
  Path getOutputZip();
  
  @Nullable
  Path getAbiOutputZip();

  NodeSourceSnapshot getSources();

  NodeSourceSnapshot getBinaryDependencies();

  BuilderArgs getBuilderArgs();

  NodeSourcePathMapper getPathMapper();

  BuildProcessLogger getBuildLogger();
}

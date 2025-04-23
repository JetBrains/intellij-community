// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel;

import org.jetbrains.jps.dependency.GraphConfiguration;

import java.nio.file.Path;

public interface BuildContext extends DiagnosticSink {
  String getTargetName();

  boolean isRebuild();
  
  Path getBaseDir();

  Path getOutputZip();

  SourceSnapshot getSources();
  
  BuilderArgs getBuilderArgs();

  GraphConfiguration getGraphConfig();

  // wipe graph, delete all caches, snapshots, storages
  void cleanBuildState();
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface DataPaths {
  String CONFIG_STATE_FILE_NAME = "config-state.dat";
  String DEP_GRAPH_FILE_NAME = "dep-graph.mv";
  String OLD_DEPS_DIR_NAME = "past-deps";

  static @NotNull Path getConfigStateStoreFile(BuildContext context) {
    return context.getDataDir().resolve(CONFIG_STATE_FILE_NAME);
  }

  static @NotNull Path getDepGraphStoreFile(BuildContext context) {
    return context.getDataDir().resolve(DEP_GRAPH_FILE_NAME);
  }

  static @NotNull Path getPastDependenciesStoreDir(BuildContext context) {
    return context.getDataDir().resolve(OLD_DEPS_DIR_NAME);
  }

  static @NotNull Path getPastJarStoreFile(BuildContext context, Path jarPath) {
    return getPastDependenciesStoreDir(context).resolve(jarPath.getFileName());
  }

  static boolean isAbiJar(String path) {
    return path.endsWith("-abi.jar"); // todo: better criterion?
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.bazel.impl.Utils;

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

  static @NotNull Path getDependenciesBackupStoreDir(BuildContext context) {
    return context.getDataDir().resolve(OLD_DEPS_DIR_NAME);
  }

  static @NotNull Path getJarBackupStoreFile(BuildContext context, Path jarPath) {
    // ensure jars with the same name won't clash in the backup store
    return getDependenciesBackupStoreDir(context).resolve(Long.toHexString(Utils.digest(jarPath.getParent().normalize().toString())) + "-" + getLibraryName(jarPath));
  }

  static String getLibraryName(Path jarPath) {
    // todo: match according to maven artifact format "${artifactId}-${version}-${classifier}.jar" and use artifactId as a library name
    return jarPath.getFileName().toString();
  }
  
  static boolean isLibraryTracked(Path path) {
    return isLibraryTracked(getLibraryName(path));
  }

  /**
   * @param path checks if the given library should be tracked: whether the corresponding graph snippet is built and analyzed
   */
  static boolean isLibraryTracked(String path) {
    return true/*path.endsWith("-abi.jar")*/;
  }
}

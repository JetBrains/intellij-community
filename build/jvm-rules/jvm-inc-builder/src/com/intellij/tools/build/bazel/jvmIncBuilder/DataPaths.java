// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder;

import com.intellij.tools.build.bazel.jvmIncBuilder.impl.Utils;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface DataPaths {
  String TRASH_DIR_NAME = "_trash";
  String CONFIG_STATE_FILE_NAME = "config-state.dat";
  String DEP_GRAPH_FILE_NAME = "dep-graph.mv";
  String OLD_DEPS_DIR_NAME = "past-deps";
  String ABI_JAR_SUFFIX = ".abi.jar";
  String DATA_DIR_NAME_SUFFIX = "-ic";
  String KOTLIN_MODULE_EXTENSION = ".kotlin_module";


  static @NotNull Path getTrashDir(BuildContext context) {
    Path dataDir = context.getDataDir();
    return getTrashDir(dataDir);
  }

  static @NotNull Path getTrashDir(Path dataDir) {
    return dataDir.resolve(TRASH_DIR_NAME);
  }

  static @NotNull Path getLibraryTrashDir(BuildContext context, Path libPath) {
    String fName = libPath.getFileName().toString();
    if (!fName.endsWith(ABI_JAR_SUFFIX)) {
      return getTrashDir(context);
    }
    return getTrashDir(libPath.resolveSibling(fName.substring(0, fName.length() - ABI_JAR_SUFFIX.length()) + DATA_DIR_NAME_SUFFIX));
  }

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
    return getJarBackupStoreFile(getDependenciesBackupStoreDir(context), jarPath);
  }

  static @NotNull Path getJarBackupStoreFile(Path dataDir, Path jarPath) {
    return dataDir.resolve(Long.toHexString(Utils.digest(jarPath.getParent().normalize().toString())) + "-" + getLibraryName(jarPath));
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
    return path.endsWith(ABI_JAR_SUFFIX);
  }
}

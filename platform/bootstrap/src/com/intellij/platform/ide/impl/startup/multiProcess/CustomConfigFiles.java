// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.startup.multiProcess;

import com.intellij.ide.plugins.DisabledPluginsState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.impl.P3SupportKt;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Describes files under {@link PathManager#getConfigDir()} which are written directly, and therefore aren't covered by 
 * {@link com.intellij.configurationStore.StreamProvider}.
 */
public final class CustomConfigFiles {
  private static final List<String> FILE_NAMES = List.of(
    DisabledPluginsState.DISABLED_PLUGINS_FILENAME,
    // Without this, building the `ultimate` project will fail with a `cannot find jdk` message in P3 mode.
    // This is a temporary hack until it is fixed properly to be able to work in P3 mode with the ultimate repository.
    "options/jdk.table.xml"
  );
  
  /**
   * Prepares a config directory ({@code currentConfigDir}) for a new process using data from the shared {@code originalConfigDir}.
   */
  public static void prepareConfigDir(@NotNull Path currentConfigDir, @NotNull Path originalConfigDir) throws IOException {
    for (String fileName : FILE_NAMES) {
      String sourceFileName =
        DisabledPluginsState.DISABLED_PLUGINS_FILENAME.equals(fileName) ? P3SupportKt.processPerProjectSupport().getDisabledPluginsFileName() 
                                                                        : fileName;  
      Path sourceFile = originalConfigDir.resolve(sourceFileName);
      Path targetFile = currentConfigDir.resolve(fileName);
      if (Files.exists(sourceFile)) {
        Files.createDirectories(targetFile.getParent());
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
      }
      else {
        Files.deleteIfExists(targetFile);
      }
    }
    DisabledPluginsState.Companion.invalidate();
  }
}

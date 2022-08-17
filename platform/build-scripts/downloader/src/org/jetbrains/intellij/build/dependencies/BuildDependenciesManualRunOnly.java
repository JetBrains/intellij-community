// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

@ApiStatus.Internal
public final class BuildDependenciesManualRunOnly {
  public static @NotNull BuildDependenciesCommunityRoot getCommunityRootFromWorkingDirectory() {
    // This method assumes the current working directory is inside intellij-based product checkout root
    Path workingDirectory = Paths.get(System.getProperty("user.dir"));

    Path current = workingDirectory;
    while (current.getParent() != null) {
      for (String pathCandidate : Arrays.asList(".", "community", "ultimate/community")) {
        Path probeFile = current.resolve(pathCandidate).resolve("intellij.idea.community.main.iml").normalize();
        if (Files.exists(probeFile)) {
          return new BuildDependenciesCommunityRoot(probeFile.getParent());
        }
      }

      current = current.getParent();
    }

    throw new IllegalStateException("IDEA Community root was not found from current working directory " + workingDirectory);
  }

  public static DependenciesProperties getDependenciesPropertiesFromWorkingDirectory() {
    return BuildDependenciesDownloader.getDependenciesProperties(getCommunityRootFromWorkingDirectory());
  }
}

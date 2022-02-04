// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jpsBootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class TestUtil {
  public static Path getCommunityRootFromWorkingDirectory() {
    // This method assumes the current working directory is inside intellij-based product checkout root
    Path workingDirectory = Paths.get(System.getProperty("user.dir"));

    Path current = workingDirectory;
    while (current.getParent() != null) {
      for (String pathCandidate : Arrays.asList(".", "community", "ultimate/community")) {
        Path probeFile = current.resolve(pathCandidate).resolve("intellij.idea.community.main.iml");
        if (Files.exists(probeFile)) {
          return probeFile.getParent();
        }
      }

      current = current.getParent();
    }

    throw new IllegalStateException("IDEA Community root was not found from current working directory " + workingDirectory);
  }
}

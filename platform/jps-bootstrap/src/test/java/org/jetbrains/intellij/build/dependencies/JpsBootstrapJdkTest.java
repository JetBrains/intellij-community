// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class JpsBootstrapJdkTest {
  @Test
  public void allJdkVariantsCouldBeDownloaded() {
    BuildDependenciesCommunityRoot communityRoot = BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory();

    for (JpsBootstrapJdk.OS os : JpsBootstrapJdk.OS.values()) {
      for (JpsBootstrapJdk.Arch arch : Arrays.asList(JpsBootstrapJdk.Arch.X86_64, JpsBootstrapJdk.Arch.ARM64)) {
        if (os == JpsBootstrapJdk.OS.WINDOWS && arch == JpsBootstrapJdk.Arch.ARM64) {
          // Not supported yet
          continue;
        }

        Path jdkHome = JpsBootstrapJdk.getJdkHome(communityRoot, os, arch);
        Path javaExecutable = JpsBootstrapJdk.getJavaExecutable(jdkHome);
        Assertions.assertTrue(Files.exists(javaExecutable));
      }
    }
  }
}

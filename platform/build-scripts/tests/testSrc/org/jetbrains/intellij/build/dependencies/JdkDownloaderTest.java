// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class JdkDownloaderTest {
  @Test
  public void allJdkVariantsCouldBeDownloaded() {
    BuildDependenciesCommunityRoot communityRoot = BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory();

    for (JdkDownloader.OS os : JdkDownloader.OS.values()) {
      for (JdkDownloader.Arch arch : Arrays.asList(JdkDownloader.Arch.X86_64, JdkDownloader.Arch.ARM64)) {
        if (os == JdkDownloader.OS.WINDOWS && arch == JdkDownloader.Arch.ARM64) {
          // Not supported yet
          continue;
        }

        Path jdkHome = JdkDownloader.getJdkHome(communityRoot, os, arch, s -> {});
        Path javaExecutable = JdkDownloader.getJavaExecutable(jdkHome);
        Assert.assertTrue(Files.exists(javaExecutable));
      }
    }
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class Jdk11DownloaderTest {
  @Test
  public void allJdkVariantsCouldBeDownloaded() {
    BuildDependenciesCommunityRoot communityRoot = BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory();

    for (Jdk11Downloader.OS os : Jdk11Downloader.OS.values()) {
      for (Jdk11Downloader.Arch arch : Arrays.asList(Jdk11Downloader.Arch.X86_64, Jdk11Downloader.Arch.ARM64)) {
        if (os == Jdk11Downloader.OS.WINDOWS && arch == Jdk11Downloader.Arch.ARM64) {
          // Not supported yet
          continue;
        }

        Path jdkHome = Jdk11Downloader.getJdkHome(communityRoot, os, arch);
        Path javaExecutable = Jdk11Downloader.getJavaExecutable(jdkHome);
        Assert.assertTrue(Files.exists(javaExecutable));
      }
    }
  }
}

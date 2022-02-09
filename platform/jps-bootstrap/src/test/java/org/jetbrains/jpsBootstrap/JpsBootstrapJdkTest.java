// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jpsBootstrap;

import com.intellij.util.system.CpuArch;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class JpsBootstrapJdkTest {
  @Test
  public void allJdkVariantsCouldBeDownloaded() throws Exception {
    Path communityRoot = TestUtil.getCommunityRootFromWorkingDirectory();

    for (JpsBootstrapJdk.OS os : JpsBootstrapJdk.OS.values()) {
      for (CpuArch arch : Arrays.asList(CpuArch.X86_64, CpuArch.ARM64)) {
        if (os == JpsBootstrapJdk.OS.WINDOWS && arch == CpuArch.ARM64) {
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

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build;


import java.util.List;

public enum JvmArchitecture {
  x64("64"), aarch64("aarch64");

  public static final List<JvmArchitecture> ALL = List.of(values());

  public static final JvmArchitecture currentJvmArch;

  static {
    String archName = System.getProperty("os.arch");
    if ("aarch64".equals(archName)) {
      currentJvmArch = aarch64;
    }
    else if ("x86_64".equals(archName) || "amd64".equals(archName)) {
      currentJvmArch = x64;
    }
    else {
      throw new IllegalStateException("Unsupported arch: $archName");
    }
  }

  public final String fileSuffix;

  JvmArchitecture(String fileSuffix) {
    this.fileSuffix = fileSuffix;
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import org.jetbrains.annotations.NotNull;

import java.io.File;

public class HotSwapFile {
  final @NotNull File file;

  public HotSwapFile(@NotNull File file) {
    this.file = file;
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.importProject;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * File dependency 
 */
record FileDescriptor(@NotNull File file) implements Dependency {
  @Override
  public int getWeight() {
    return 10;
  }
}

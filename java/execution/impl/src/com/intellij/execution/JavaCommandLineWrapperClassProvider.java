// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.openapi.projectRoots.CommandLineWrapperClassProvider;
import com.intellij.rt.execution.CommandLineWrapper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class JavaCommandLineWrapperClassProvider implements CommandLineWrapperClassProvider {
  @Override
  public @NotNull Class<?> getCommandLineWrapperClass() {
    return CommandLineWrapper.class;
  }
}

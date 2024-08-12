// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.projectRoots.JdkFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class JdkFinderImpl implements JdkFinder {
  @Override
  public @NotNull List<String> suggestHomePaths() {
    return JavaHomeFinder.suggestHomePaths();
  }

  @Override
  public @Nullable String defaultJavaLocation() {
    return JavaHomeFinder.defaultJavaLocation();
  }
}

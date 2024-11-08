// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JdkFinder;
import com.intellij.platform.eel.provider.EelProviderUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class JdkFinderImpl implements JdkFinder {
  @Override
  public @NotNull List<String> suggestHomePaths() {
    return JavaHomeFinder.suggestHomePaths();
  }

  @Override
  public @Nullable String defaultJavaLocation() {
    return JavaHomeFinder.defaultJavaLocation(null);
  }

  @Override
  public @NotNull List<@NotNull String> suggestHomePaths(@Nullable Project project) {
    return JavaHomeFinder.suggestHomePaths(EelProviderUtil.getEelApiBlocking(project), false);
  }
}

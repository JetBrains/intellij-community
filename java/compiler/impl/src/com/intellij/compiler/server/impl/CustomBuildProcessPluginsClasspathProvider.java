// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server.impl;

import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Adds custom plugins configured in {@link BuildProcessCustomPluginsConfiguration} to the build process' classpath.
 */
public final class CustomBuildProcessPluginsClasspathProvider extends BuildProcessParametersProvider {
  private final Project myProject;

  public CustomBuildProcessPluginsClasspathProvider(Project project) {
    myProject = project;
  }

  @Override
  public @NotNull List<String> getClassPath() {
    return BuildProcessCustomPluginsConfiguration.getInstance(myProject).getCustomPluginsClasspath();
  }
}

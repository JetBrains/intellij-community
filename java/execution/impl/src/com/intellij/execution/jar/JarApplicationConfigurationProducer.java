// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.jar;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JarApplicationConfigurationProducer extends LazyRunConfigurationProducer<JarApplicationConfiguration> {
  @Override
  public @NotNull ConfigurationFactory getConfigurationFactory() {
    return JarApplicationConfigurationType.getInstance();
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull JarApplicationConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    VirtualFile file = getJarFileFromContext(context);
    if (file != null) {
      configuration.setName(file.getName());
      configuration.setJarPath(file.getPath());
      return true;
    }
    return false;
  }

  private static @Nullable VirtualFile getJarFileFromContext(ConfigurationContext context) {
    Location location = context.getLocation();
    if (location == null) return null;

    VirtualFile file = location.getVirtualFile();
    return file != null && FileUtilRt.extensionEquals(file.getName(), "jar") ? file : null;
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull JarApplicationConfiguration configuration, @NotNull ConfigurationContext context) {
    VirtualFile file = getJarFileFromContext(context);
    return file != null && VfsUtilCore.pathEqualsTo(file, configuration.getJarPath());
  }
}

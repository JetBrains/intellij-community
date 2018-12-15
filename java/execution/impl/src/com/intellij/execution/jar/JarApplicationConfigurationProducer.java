// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.jar;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public final class JarApplicationConfigurationProducer extends LazyRunConfigurationProducer<JarApplicationConfiguration> {
  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return JarApplicationConfigurationType.getInstance();
  }

  @Override
  protected boolean setupConfigurationFromContext(JarApplicationConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    VirtualFile file = getJarFileFromContext(context);
    if (file != null) {
      configuration.setName(file.getName());
      configuration.setJarPath(file.getPath());
      return true;
    }
    return false;
  }

  @Nullable
  private static VirtualFile getJarFileFromContext(ConfigurationContext context) {
    Location location = context.getLocation();
    if (location == null) return null;

    VirtualFile file = location.getVirtualFile();
    return file != null && FileUtilRt.extensionEquals(file.getName(), "jar") ? file : null;
  }

  @Override
  public boolean isConfigurationFromContext(JarApplicationConfiguration configuration, ConfigurationContext context) {
    VirtualFile file = getJarFileFromContext(context);
    return file != null && FileUtil.pathsEqual(file.getPath(), configuration.getJarPath());
  }
}

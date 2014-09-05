/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.jar;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class JarApplicationConfigurationProducer extends RunConfigurationProducer<JarApplicationConfiguration> {
  public JarApplicationConfigurationProducer() {
    super(JarApplicationConfigurationType.getInstance());
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

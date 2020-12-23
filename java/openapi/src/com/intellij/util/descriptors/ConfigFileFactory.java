// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.descriptors;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public abstract class ConfigFileFactory {
  public static ConfigFileFactory getInstance() {
    return ApplicationManager.getApplication().getService(ConfigFileFactory.class);
  }

  public abstract ConfigFileMetaDataProvider createMetaDataProvider(ConfigFileMetaData... metaData);

  public abstract ConfigFileInfoSet createConfigFileInfoSet(ConfigFileMetaDataProvider metaDataProvider);

  public abstract ConfigFileContainer createConfigFileContainer(Project project, ConfigFileMetaDataProvider metaDataProvider,
                                                              ConfigFileInfoSet configuration);

  public abstract ConfigFileMetaDataRegistry createMetaDataRegistry();

  @Nullable
  public abstract VirtualFile createFile(@Nullable Project project, String url, ConfigFileVersion version, final boolean forceNew);

  public abstract ConfigFileContainer createSingleFileContainer(Project project, ConfigFileMetaData metaData);
}

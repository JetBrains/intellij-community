/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.util.descriptors;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class ConfigFileFactory {
  public static ConfigFileFactory getInstance() {
    return ServiceManager.getService(ConfigFileFactory.class);
  }

  public abstract ConfigFileMetaDataProvider createMetaDataProvider(ConfigFileMetaData... metaDatas);

  public abstract ConfigFileInfoSet createConfigFileInfoSet(ConfigFileMetaDataProvider metaDataProvider);

  public abstract ConfigFileContainer createConfigFileContainer(Project project, ConfigFileMetaDataProvider metaDataProvider,
                                                              ConfigFileInfoSet configuration);

  public abstract ConfigFileMetaDataRegistry createMetaDataRegistry();

  @Nullable
  public abstract VirtualFile createFile(@Nullable Project project, String url, ConfigFileVersion version, final boolean forceNew);

  public abstract ConfigFileContainer createSingleFileContainer(Project project, ConfigFileMetaData metaData);
}

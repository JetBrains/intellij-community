/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.util.JDOMExternalizable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public interface ConfigFileInfoSet extends JDOMExternalizable {
  void addConfigFile(ConfigFileInfo descriptor);

  void addConfigFile(ConfigFileMetaData metaData, @NonNls String url);

  void removeConfigFile(ConfigFileInfo descriptor);

  void replaceConfigFile(ConfigFileMetaData metaData, @NonNls String newUrl);

  void updateConfigFile(ConfigFile configFile);

  void removeConfigFiles(ConfigFileMetaData... metaData);

  List<ConfigFileInfo> getConfigFileInfos();

  void setConfigFileInfos(Collection<? extends ConfigFileInfo> descriptors);

  void setConfigFileItems(@NotNull List<ConfigFileItem> configFileItems);

  ConfigFileMetaDataProvider getMetaDataProvider();

  @Nullable
  ConfigFileInfo getConfigFileInfo(ConfigFileMetaData metaData);

  void setContainer(@NotNull ConfigFileContainer container);
}

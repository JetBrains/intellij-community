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
package com.intellij.util.descriptors.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.descriptors.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

public final class ConfigFileInfoSetImpl implements ConfigFileInfoSet {
  private static final Logger LOG = Logger.getInstance(ConfigFileInfoSetImpl.class);
  @NonNls private static final String ELEMENT_NAME = "deploymentDescriptor";
  @NonNls private static final String ID_ATTRIBUTE = "name";
  @NonNls private static final String URL_ATTRIBUTE = "url";
  private final MultiMap<ConfigFileMetaData, ConfigFileInfo> configFiles = new MultiMap<>();
  private @Nullable ConfigFileContainerImpl myContainer;
  private final ConfigFileMetaDataProvider myMetaDataProvider;

  public ConfigFileInfoSetImpl(final ConfigFileMetaDataProvider metaDataProvider) {
    myMetaDataProvider = metaDataProvider;
  }

  @Override
  public void addConfigFile(ConfigFileInfo descriptor) {
    configFiles.putValue(descriptor.getMetaData(), descriptor);
    onChange();
  }

  @Override
  public void addConfigFile(final ConfigFileMetaData metaData, final String url) {
    addConfigFile(new ConfigFileInfo(metaData, url));
  }

  @Override
  public void removeConfigFile(ConfigFileInfo descriptor) {
    configFiles.remove(descriptor.getMetaData(), descriptor);
    onChange();
  }

  @Override
  public void replaceConfigFile(ConfigFileMetaData metaData, String newUrl) {
    configFiles.remove(metaData);
    addConfigFile(new ConfigFileInfo(metaData, newUrl));
  }

  public void updateConfigFile(ConfigFile configFile) {
    configFiles.remove(configFile.getMetaData(), configFile.getInfo());
    ConfigFileInfo info = new ConfigFileInfo(configFile.getMetaData(), configFile.getUrl());
    configFiles.putValue(info.getMetaData(), info);
    ((ConfigFileImpl)configFile).setInfo(info);
  }

  @Override
  public void removeConfigFiles(final ConfigFileMetaData... metaData) {
    for (ConfigFileMetaData data : metaData) {
      configFiles.remove(data);
    }
    onChange();
  }

  @Override
  public @Nullable ConfigFileInfo getConfigFileInfo(ConfigFileMetaData metaData) {
    Collection<ConfigFileInfo> descriptors = configFiles.get(metaData);
    return descriptors.isEmpty() ? null : descriptors.iterator().next();
  }

  @Override
  public List<ConfigFileInfo> getConfigFileInfos() {
    return List.copyOf(configFiles.values());
  }

  @Override
  public void setConfigFileInfos(Collection<? extends ConfigFileInfo> descriptors) {
    configFiles.clear();
    for (ConfigFileInfo descriptor : descriptors) {
      configFiles.putValue(descriptor.getMetaData(), descriptor);
    }
    onChange();
  }

  private void onChange() {
    if (myContainer != null) {
      myContainer.updateDescriptors(configFiles);
    }
  }

  @Override
  public ConfigFileMetaDataProvider getMetaDataProvider() {
    return myMetaDataProvider;
  }

  @Override
  public void readExternal(final Element element) throws InvalidDataException {
    configFiles.clear();
    List<Element> children = element.getChildren(ELEMENT_NAME);
    for (Element child : children) {
      final String id = child.getAttributeValue(ID_ATTRIBUTE);
      if (id != null) {
        final ConfigFileMetaData metaData = myMetaDataProvider.findMetaData(id);
        if (metaData != null) {
          final String url = child.getAttributeValue(URL_ATTRIBUTE);
          if (url == null) throw new InvalidDataException(URL_ATTRIBUTE + " attribute not specified for " + id + " descriptor");
          configFiles.putValue(metaData, new ConfigFileInfo(metaData, url));
        }
      }
    }
    onChange();
  }

  @Override
  public void writeExternal(final Element element) throws WriteExternalException {
    final TreeSet<ConfigFileInfo> sortedConfigFiles = new TreeSet<>((o1, o2) -> {
      final int id = Comparing.compare(o1.getMetaData().getId(), o2.getMetaData().getId());
      return id != 0 ? id : Comparing.compare(o1.getUrl(), o2.getUrl());
    });
    sortedConfigFiles.addAll(configFiles.values());
    for (ConfigFileInfo configuration : sortedConfigFiles) {
      final Element child = new Element(ELEMENT_NAME);
      final ConfigFileMetaData metaData = configuration.getMetaData();
      child.setAttribute(ID_ATTRIBUTE, metaData.getId());
      child.setAttribute(URL_ATTRIBUTE, configuration.getUrl());
      element.addContent(child);
    }
  }

  public void setContainer(@NotNull ConfigFileContainerImpl container) {
    LOG.assertTrue(myContainer == null);
    myContainer = container;
    myContainer.updateDescriptors(configFiles);
  }
}

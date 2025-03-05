// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

public final class ConfigFileInfoSetImpl implements ConfigFileInfoSet {
  private static final Logger LOG = Logger.getInstance(ConfigFileInfoSetImpl.class);
  private static final @NonNls String ELEMENT_NAME = "deploymentDescriptor";
  private static final @NonNls String ID_ATTRIBUTE = "name";
  private static final @NonNls String URL_ATTRIBUTE = "url";
  private final MultiMap<ConfigFileMetaData, ConfigFileInfo> configFiles = new MultiMap<>();
  private @Nullable ConfigFileContainerImpl myContainer;
  private final ConfigFileMetaDataProvider myMetaDataProvider;

  public ConfigFileInfoSetImpl(final @NotNull ConfigFileMetaDataProvider metaDataProvider) {
    myMetaDataProvider = metaDataProvider;
  }

  @Override
  public void addConfigFile(@NotNull ConfigFileInfo descriptor) {
    configFiles.putValue(descriptor.getMetaData(), descriptor);
    onChange();
  }

  @Override
  public void addConfigFile(final @NotNull ConfigFileMetaData metaData, final @NotNull String url) {
    addConfigFile(new ConfigFileInfo(metaData, url));
  }

  @Override
  public void removeConfigFile(@NotNull ConfigFileInfo descriptor) {
    configFiles.remove(descriptor.getMetaData(), descriptor);
    onChange();
  }

  @Override
  public void replaceConfigFile(@NotNull ConfigFileMetaData metaData, @NotNull String newUrl) {
    configFiles.remove(metaData);
    addConfigFile(new ConfigFileInfo(metaData, newUrl));
  }

  @Override
  public void updateConfigFile(@NotNull ConfigFile configFile) {
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
  public @Nullable ConfigFileInfo getConfigFileInfo(@NotNull ConfigFileMetaData metaData) {
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

  @Override
  public void setConfigFileItems(@NotNull List<ConfigFileItem> configFileItems) {
    var configFileInfos = new ArrayList<ConfigFileInfo>();
    for (var configFileItem : configFileItems) {
      var metadata = myMetaDataProvider.findMetaData(configFileItem.getId());
      if (null != metadata) {
        configFileInfos.add(new ConfigFileInfo(metadata, configFileItem.getUrl()));
      }
    }
    setConfigFileInfos(configFileInfos);
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

  @Override
  public void setContainer(@NotNull ConfigFileContainer container) {
    LOG.assertTrue(myContainer == null);
    myContainer = (ConfigFileContainerImpl)container;
    myContainer.updateDescriptors(configFiles);
  }
}

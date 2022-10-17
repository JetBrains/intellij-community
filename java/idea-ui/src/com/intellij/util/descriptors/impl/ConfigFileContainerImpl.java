// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.descriptors.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.descriptors.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ConfigFileContainerImpl extends SimpleModificationTracker implements ConfigFileContainer {
  private final Project project;
  private final EventDispatcher<ConfigFileListener> myDispatcher = EventDispatcher.create(ConfigFileListener.class);
  private final MultiMap<ConfigFileMetaData, ConfigFile> configFiles = new MultiMap<>();
  private List<ConfigFile> myCachedConfigFiles;
  private final ConfigFileMetaDataProvider metaDataProvider;
  private final ConfigFileInfoSetImpl configuration;

  public ConfigFileContainerImpl(@NotNull Project project,
                                 @NotNull ConfigFileMetaDataProvider descriptorMetaDataProvider,
                                 @NotNull ConfigFileInfoSetImpl configuration) {
    this.configuration = configuration;
    metaDataProvider = descriptorMetaDataProvider;
    this.project = project;

    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
      @Override
      public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
          fileChanged(event.getFile());
        }
      }

      @Override
      public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        fileChanged(event.getFile());
      }
    }, this);
    this.configuration.setContainer(this);
  }

  private void fileChanged(VirtualFile file) {
    for (ConfigFile descriptor : configFiles.values()) {
      VirtualFile virtualFile = descriptor.getVirtualFile();
      if (virtualFile != null && VfsUtilCore.isAncestor(file, virtualFile, false)) {
        configuration.updateConfigFile(descriptor);
        fireDescriptorChanged(descriptor);
      }
    }
  }

  @Override
  public @Nullable ConfigFile getConfigFile(ConfigFileMetaData metaData) {
    return ContainerUtil.getFirstItem(configFiles.get(metaData));
  }

  @Override
  public List<ConfigFile> getConfigFiles() {
    List<ConfigFile> result = myCachedConfigFiles;
    if (result == null) {
      result = List.copyOf(configFiles.values());
      myCachedConfigFiles = result;
    }
    return result;
  }

  @Override
  public Project getProject() {
    return project;
  }

  @Override
  public void addListener(ConfigFileListener listener, Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  void fireDescriptorChanged(@NotNull ConfigFile descriptor) {
    incModificationCount();
    myDispatcher.getMulticaster().configFileChanged(descriptor);
  }


  @Override
  public ConfigFileInfoSet getConfiguration() {
    return configuration;
  }

  @Override
  public void dispose() {
  }

  @Override
  public void addListener(final ConfigFileListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void removeListener(final ConfigFileListener listener) {
    myDispatcher.removeListener(listener);
  }

  public ConfigFileMetaDataProvider getMetaDataProvider() {
    return metaDataProvider;
  }

  public void updateDescriptors(@NotNull MultiMap<ConfigFileMetaData, ConfigFileInfo> descriptorMap) {
    Set<ConfigFile> toDelete = configFiles.isEmpty() ? Collections.emptySet() : new HashSet<>(configFiles.values());
    Set<ConfigFile> added = null;

    for (Map.Entry<ConfigFileMetaData, Collection<ConfigFileInfo>> entry : descriptorMap.entrySet()) {
      ConfigFileMetaData metaData = entry.getKey();
      Set<ConfigFileInfo> newDescriptors = new HashSet<>(entry.getValue());

      if (configFiles.containsKey(metaData)) {
        for (ConfigFile descriptor : configFiles.get(metaData)) {
          if (newDescriptors.remove(descriptor.getInfo()) && !toDelete.isEmpty()) {
            toDelete.remove(descriptor);
          }
        }
      }

      for (ConfigFileInfo configuration : newDescriptors) {
        ConfigFileImpl configFile = new ConfigFileImpl(this, configuration);
        Disposer.register(this, configFile);
        configFiles.putValue(metaData, configFile);
        if (added == null) {
          added = new HashSet<>();
        }
        added.add(configFile);
      }
    }

    for (ConfigFile descriptor : toDelete) {
      configFiles.remove(descriptor.getMetaData(), descriptor);
      Disposer.dispose(descriptor);
    }

    myCachedConfigFiles = null;
    if (added != null) {
      for (ConfigFile configFile : added) {
        incModificationCount();
        myDispatcher.getMulticaster().configFileAdded(configFile);
      }
    }
    for (ConfigFile configFile : toDelete) {
      incModificationCount();
      myDispatcher.getMulticaster().configFileRemoved(configFile);
    }
  }
}

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.descriptors.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.descriptors.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ConfigFileContainerImpl extends SimpleModificationTracker implements ConfigFileContainer {
  private final Project myProject;
  private final EventDispatcher<ConfigFileListener> myDispatcher = EventDispatcher.create(ConfigFileListener.class);
  private final MultiValuesMap<ConfigFileMetaData, ConfigFile> myConfigFiles = new MultiValuesMap<>();
  private ConfigFile[] myCachedConfigFiles;
  private final ConfigFileMetaDataProvider myMetaDataProvider;
  private final ConfigFileInfoSetImpl myConfiguration;

  public ConfigFileContainerImpl(final Project project, final ConfigFileMetaDataProvider descriptorMetaDataProvider,
                                       final ConfigFileInfoSetImpl configuration) {
    myConfiguration = configuration;
    myMetaDataProvider = descriptorMetaDataProvider;
    myProject = project;
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
      @Override
      public void propertyChanged(@NotNull final VirtualFilePropertyEvent event) {
        if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
          fileChanged(event.getFile());
        }
      }

      @Override
      public void fileMoved(@NotNull final VirtualFileMoveEvent event) {
        fileChanged(event.getFile());
      }
    }, this);
    myConfiguration.setContainer(this);
  }

  private void fileChanged(final VirtualFile file) {
    for (ConfigFile descriptor : myConfigFiles.values()) {
      final VirtualFile virtualFile = descriptor.getVirtualFile();
      if (virtualFile != null && VfsUtilCore.isAncestor(file, virtualFile, false)) {
        myConfiguration.updateConfigFile(descriptor);
        fireDescriptorChanged(descriptor);
      }
    }
  }

  @Override
  @Nullable
  public ConfigFile getConfigFile(ConfigFileMetaData metaData) {
    return ContainerUtil.getFirstItem(myConfigFiles.get(metaData));
  }

  @Override
  public ConfigFile[] getConfigFiles() {
    if (myCachedConfigFiles == null) {
      final Collection<ConfigFile> descriptors = myConfigFiles.values();
      myCachedConfigFiles = descriptors.toArray(ConfigFile.EMPTY_ARRAY);
    }
    return myCachedConfigFiles;
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public void addListener(final ConfigFileListener listener, final Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  void fireDescriptorChanged(@NotNull ConfigFile descriptor) {
    incModificationCount();
    myDispatcher.getMulticaster().configFileChanged(descriptor);
  }


  @Override
  public ConfigFileInfoSet getConfiguration() {
    return myConfiguration;
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
    return myMetaDataProvider;
  }

  public void updateDescriptors(@NotNull MultiValuesMap<ConfigFileMetaData, ConfigFileInfo> descriptorsMap) {
    Set<ConfigFile> toDelete = myConfigFiles.isEmpty() ? Collections.emptySet() : new HashSet<>(myConfigFiles.values());
    Set<ConfigFile> added = null;

    for (Map.Entry<ConfigFileMetaData, Collection<ConfigFileInfo>> entry : descriptorsMap.entrySet()) {
      ConfigFileMetaData metaData = entry.getKey();
      Set<ConfigFileInfo> newDescriptors = new HashSet<>(entry.getValue());
      final Collection<ConfigFile> oldDescriptors = myConfigFiles.get(metaData);
      if (oldDescriptors != null) {
        for (ConfigFile descriptor : oldDescriptors) {
          if (newDescriptors.remove(descriptor.getInfo())) {
            toDelete.remove(descriptor);
          }
        }
      }
      for (ConfigFileInfo configuration : newDescriptors) {
        final ConfigFileImpl configFile = new ConfigFileImpl(this, configuration);
        Disposer.register(this, configFile);
        myConfigFiles.put(metaData, configFile);
        if (added == null) {
          added = new HashSet<>();
        }
        added.add(configFile);
      }
    }

    for (ConfigFile descriptor : toDelete) {
      myConfigFiles.remove(descriptor.getMetaData(), descriptor);
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

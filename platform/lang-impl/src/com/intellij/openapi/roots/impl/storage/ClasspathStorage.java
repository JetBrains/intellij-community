/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl.storage;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.components.impl.stores.StateStorageBase;
import com.intellij.openapi.components.impl.stores.StateStorageManager;
import com.intellij.openapi.components.impl.stores.StorageManagerListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl.ModuleRootManagerState;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;

import java.io.IOException;
import java.util.List;
import java.util.Set;

// Boolean - false as not loaded, true as loaded
public final class ClasspathStorage extends StateStorageBase<Boolean> {
  private static final Logger LOG = Logger.getInstance(ClasspathStorage.class);

  private final ClasspathStorageProvider.ClasspathConverter myConverter;

  private final TrackingPathMacroSubstitutor myPathMacroSubstitutor;

  public ClasspathStorage(@NotNull final Module module, @NotNull StateStorageManager storageManager) {
    String storageType = module.getOptionValue(JpsProjectLoader.CLASSPATH_ATTRIBUTE);
    if (storageType == null) {
      throw new IllegalStateException("Classpath storage requires non-default storage type");
    }

    ClasspathStorageProvider provider = getProvider(storageType);
    if (provider == null) {
      throw new IllegalStateException("Classpath storage provider not found, please ensure that Eclipse plugin is installed");
    }
    myConverter = provider.createConverter(module);

    myPathMacroSubstitutor = storageManager.getMacroSubstitutor();

    final List<String> paths = myConverter.getFilePaths();
    MessageBusConnection busConnection = module.getMessageBus().connect();
    busConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (!event.isFromRefresh() || !(event instanceof VFileContentChangeEvent)) {
            continue;
          }

          for (String path : paths) {
            if (path.equals(event.getPath())) {
              module.getMessageBus().syncPublisher(StateStorageManager.STORAGE_TOPIC).storageFileChanged(event, ClasspathStorage.this, module);
              return;
            }
          }
        }
      }
    });

    busConnection.subscribe(StateStorageManager.STORAGE_TOPIC, new StorageManagerListener() {
      private String fileNameToModuleName(@NotNull String fileName) {
        return fileName.substring(0, fileName.length() - ModuleFileType.DOT_DEFAULT_EXTENSION.length());
      }

      @Override
      public void storageFileChanged(@NotNull VFileEvent event, @NotNull StateStorage storage, @NotNull ComponentManager componentManager) {
        assert componentManager == module;
        if (!(event instanceof VFilePropertyChangeEvent)) {
          return;
        }

        VFilePropertyChangeEvent propertyEvent = (VFilePropertyChangeEvent)event;
        if (propertyEvent.getPropertyName().equals(VirtualFile.PROP_NAME)) {
          String oldFileName = (String)propertyEvent.getOldValue();
          if (oldFileName.endsWith(ModuleFileType.DOT_DEFAULT_EXTENSION)) {
            ClasspathStorageProvider provider = getProvider(ClassPathStorageUtil.getStorageType(module));
            if (provider != null) {
              provider.moduleRenamed(module, fileNameToModuleName(oldFileName), fileNameToModuleName((String)propertyEvent.getNewValue()));
            }
          }
        }
      }
    });
  }

  @Nullable
  @Override
  public <S> S deserializeState(@Nullable Element serializedState, @NotNull Class<S> stateClass, @Nullable S mergeInto) {
    if (serializedState == null) {
      return null;
    }

    ModuleRootManagerState state = new ModuleRootManagerState();
    state.readExternal(serializedState);
    //noinspection unchecked
    return (S)state;
  }

  @Override
  protected boolean hasState(@NotNull Boolean storageData, @NotNull String componentName) {
    return !storageData;
  }

  @Nullable
  @Override
  public Element getSerializedState(@NotNull Boolean storageData, Object component, @NotNull String componentName, boolean archive) {
    if (storageData) {
      return null;
    }

    Element element = new Element("component");
    ModifiableRootModel model = null;
    AccessToken token = ReadAction.start();
    try {
      model = ((ModuleRootManagerImpl)component).getModifiableModel();
      // IDEA-137969 Eclipse integration: external remove of classpathentry is not synchronized
      model.clear();
      try {
        myConverter.readClasspath(model);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      ((RootModelImpl)model).writeExternal(element);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    finally {
      try {
        token.finish();
      }
      finally {
        if (model != null) {
          model.dispose();
        }
      }
    }

    if (myPathMacroSubstitutor != null) {
      myPathMacroSubstitutor.expandPaths(element);
      myPathMacroSubstitutor.addUnknownMacros("NewModuleRootManager", PathMacrosCollector.getMacroNames(element));
    }

    getStorageDataRef().set(true);
    return element;
  }

  @NotNull
  @Override
  protected Boolean loadData() {
    return false;
  }

  @Override
  @NotNull
  public ExternalizationSession startExternalization() {
    return myConverter.startExternalization();
  }

  @Override
  public void analyzeExternalChangesAndUpdateIfNeed(@NotNull Set<String> componentNames) {
    // if some file changed, so, changed
    componentNames.add("NewModuleRootManager");
    getStorageDataRef().set(false);
  }

  @Nullable
  public static ClasspathStorageProvider getProvider(@NotNull String type) {
    if (type.equals(ClassPathStorageUtil.DEFAULT_STORAGE)) {
      return null;
    }

    for (ClasspathStorageProvider provider : ClasspathStorageProvider.EXTENSION_POINT_NAME.getExtensions()) {
      if (type.equals(provider.getID())) {
        return provider;
      }
    }
    return null;
  }

  @NotNull
  public static String getStorageRootFromOptions(@NotNull Module module) {
    String moduleRoot = ModuleUtilCore.getModuleDirPath(module);
    String storageRef = module.getOptionValue(JpsProjectLoader.CLASSPATH_DIR_ATTRIBUTE);
    if (storageRef == null) {
      return moduleRoot;
    }

    storageRef = FileUtil.toSystemIndependentName(storageRef);
    if (SystemInfo.isWindows ? FileUtil.isAbsolutePlatformIndependent(storageRef) : FileUtil.isUnixAbsolutePath(storageRef)) {
      return storageRef;
    }
    else {
      return moduleRoot + '/' + storageRef;
    }
  }

  public static void setStorageType(@NotNull ModuleRootModel model, @NotNull String storageId) {
    Module module = model.getModule();
    String oldStorageType = ClassPathStorageUtil.getStorageType(module);
    if (oldStorageType.equals(storageId)) {
      return;
    }

    ClasspathStorageProvider provider = getProvider(oldStorageType);
    if (provider != null) {
      provider.detach(module);
    }

    provider = getProvider(storageId);
    if (provider == null) {
      module.clearOption(JpsProjectLoader.CLASSPATH_ATTRIBUTE);
      module.clearOption(JpsProjectLoader.CLASSPATH_DIR_ATTRIBUTE);
    }
    else {
      module.setOption(JpsProjectLoader.CLASSPATH_ATTRIBUTE, storageId);
      module.setOption(JpsProjectLoader.CLASSPATH_DIR_ATTRIBUTE, provider.getContentRoot(model));
    }
  }

  public static void modulePathChanged(Module module, String newPath) {
    ClasspathStorageProvider provider = getProvider(ClassPathStorageUtil.getStorageType(module));
    if (provider != null) {
      provider.modulePathChanged(module, newPath);
    }
  }
}

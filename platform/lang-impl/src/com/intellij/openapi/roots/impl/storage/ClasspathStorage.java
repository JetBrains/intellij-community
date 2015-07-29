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
package com.intellij.openapi.roots.impl.storage;

import com.intellij.application.options.PathMacrosCollector;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.components.impl.stores.StateStorageBase;
import com.intellij.openapi.components.impl.stores.StorageDataBase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl.ModuleRootManagerState;
import com.intellij.openapi.roots.impl.RootModelImpl;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.tracker.VirtualFileTracker;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ClasspathStorage extends StateStorageBase<ClasspathStorage.MyStorageData> {
  @NonNls public static final String SPECIAL_STORAGE = "special";

  private final ClasspathStorageProvider.ClasspathConverter myConverter;

  public ClasspathStorage(@NotNull Module module, @NotNull IComponentStore moduleStore) {
    super(moduleStore.getStateStorageManager().getMacroSubstitutor());

    ClasspathStorageProvider provider = getProvider(ClassPathStorageUtil.getStorageType(module));
    assert provider != null;
    myConverter = provider.createConverter(module);
    assert myConverter != null;

    VirtualFileTracker virtualFileTracker = ServiceManager.getService(VirtualFileTracker.class);
    if (virtualFileTracker != null) {
      List<String> urls = myConverter.getFileUrls();
      for (String url : urls) {
        final Listener listener = module.getProject().getMessageBus().syncPublisher(PROJECT_STORAGE_TOPIC);
        virtualFileTracker.addTracker(url, new VirtualFileAdapter() {
          @Override
          public void contentsChanged(@NotNull VirtualFileEvent event) {
            listener.storageFileChanged(event, ClasspathStorage.this);
          }
        }, true, module);
      }
    }
  }

  @Nullable
  @Override
  protected <S> S deserializeState(@Nullable Element serializedState, @NotNull Class<S> stateClass, @Nullable S mergeInto) {
    if (serializedState == null) {
      return null;
    }

    ModuleRootManagerState state = new ModuleRootManagerState();
    state.readExternal(serializedState);
    //noinspection unchecked
    return (S)state;
  }

  static class MyStorageData extends StorageDataBase {
    private boolean loaded;

    @NotNull
    @Override
    public Set<String> getComponentNames() {
      return Collections.emptySet();
    }

    @Override
    public boolean hasState(@NotNull String componentName) {
      return !loaded;
    }
  }

  @Nullable
  @Override
  protected Element getStateAndArchive(@NotNull MyStorageData storageData, Object component, @NotNull String componentName) {
    if (storageData.loaded) {
      return null;
    }

    Element element = new Element("component");
    try {
      ModifiableRootModel model = null;
      try {
        model = ((ModuleRootManagerImpl)component).getModifiableModel();
        // IDEA-137969 Eclipse integration: external remove of classpathentry is not synchronized
        model.clear();
        myConverter.readClasspath(model);
        ((RootModelImpl)model).writeExternal(element);
      }
      catch (WriteExternalException e) {
        LOG.error(e);
      }
      finally {
        if (model != null) {
          model.dispose();
        }
      }

      if (myPathMacroSubstitutor != null) {
        myPathMacroSubstitutor.expandPaths(element);
        myPathMacroSubstitutor.addUnknownMacros("NewModuleRootManager", PathMacrosCollector.getMacroNames(element));
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    storageData.loaded = true;
    return element;
  }

  @Override
  protected MyStorageData loadData() {
    return new MyStorageData();
  }

  @Override
  @NotNull
  public ExternalizationSession startExternalization() {
    return myConverter.startExternalization();
  }

  @Override
  public void analyzeExternalChangesAndUpdateIfNeed(@NotNull Collection<VirtualFile> changedFiles, @NotNull Set<String> componentNames) {
    // if some file changed, so, changed
    componentNames.add("NewModuleRootManager");
    if (myStorageData != null) {
      myStorageData.loaded = false;
    }
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
  public static String getModuleDir(@NotNull Module module) {
    return PathUtil.getParentPath(FileUtilRt.toSystemIndependentName(module.getModuleFilePath()));
  }

  @NotNull
  public static String getStorageRootFromOptions(@NotNull Module module) {
    String moduleRoot = getModuleDir(module);
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

  public static void moduleRenamed(@NotNull Module module, @NotNull String newName) {
    ClasspathStorageProvider provider = getProvider(ClassPathStorageUtil.getStorageType(module));
    if (provider != null) {
      provider.moduleRenamed(module, newName);
    }
  }

  public static void modulePathChanged(Module module, String newPath) {
    ClasspathStorageProvider provider = getProvider(ClassPathStorageUtil.getStorageType(module));
    if (provider != null) {
      provider.modulePathChanged(module, newPath);
    }
  }
}

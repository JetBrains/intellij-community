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
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.*;
import com.intellij.openapi.components.impl.stores.IModuleStore;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
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
import java.util.List;
import java.util.Set;

public class ClasspathStorage implements StateStorage {
  @NonNls public static final String SPECIAL_STORAGE = "special";

  @NonNls public static final String CLASSPATH_DIR_OPTION = JpsProjectLoader.CLASSPATH_DIR_ATTRIBUTE;

  private final ClasspathStorageProvider.ClasspathConverter myConverter;
  private final TrackingPathMacroSubstitutor myTrackingPathMacroSubstitutor;

  public ClasspathStorage(@NotNull Module module, @NotNull IModuleStore moduleStore) {
    ClasspathStorageProvider provider = getProvider(ClassPathStorageUtil.getStorageType(module));
    assert provider != null;
    myConverter = provider.createConverter(module);
    assert myConverter != null;

    myTrackingPathMacroSubstitutor = moduleStore.getStateStorageManager().getMacroSubstitutor();

    VirtualFileTracker virtualFileTracker = ServiceManager.getService(VirtualFileTracker.class);
    if (virtualFileTracker != null) {
      List<String> urls = myConverter.getFileSet().getFileUrls();
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

  @Override
  @Nullable
  public <T> T getState(final Object component, @NotNull String componentName, @NotNull Class<T> stateClass, @Nullable T mergeInto) {
    assert component instanceof ModuleRootManager;
    assert componentName.equals("NewModuleRootManager");
    assert stateClass == ModuleRootManagerImpl.ModuleRootManagerState.class;

    try {
      Element element = new Element("component");
      ModifiableRootModel model = null;
      try {
        model = ((ModuleRootManagerImpl)component).getModifiableModel();
        myConverter.getClasspath(model, element);
      }
      finally {
        if (model != null) {
          model.dispose();
        }
      }

      myTrackingPathMacroSubstitutor.expandPaths(element);
      myTrackingPathMacroSubstitutor.addUnknownMacros(componentName, PathMacrosCollector.getMacroNames(element));

      ModuleRootManagerImpl.ModuleRootManagerState moduleRootManagerState = new ModuleRootManagerImpl.ModuleRootManagerState();
      moduleRootManagerState.readExternal(element);
      //noinspection unchecked
      return (T)moduleRootManagerState;
    }
    catch (InvalidDataException e) {
      throw new StateStorageException(e.getMessage());
    }
    catch (IOException e) {
      throw new StateStorageException(e.getMessage());
    }
  }

  @Override
  public boolean hasState(@Nullable final Object component, @NotNull final String componentName, final Class<?> aClass, final boolean reloadData) {
    return true;
  }

  @Override
  @NotNull
  public ExternalizationSession startExternalization() {
    return new ClasspathSaveSession();
  }

  @Override
  public void analyzeExternalChangesAndUpdateIfNeed(@NotNull Collection<VirtualFile> changedFiles, @NotNull Set<String> componentNames) {
    componentNames.add("NewModuleRootManager");
  }

  @Nullable
  public static ClasspathStorageProvider getProvider(@NotNull String type) {
    for (ClasspathStorageProvider provider : ClasspathStorageProvider.EXTENSION_POINT_NAME.getExtensions()) {
      if (type.equals(provider.getID())) {
        return provider;
      }
    }
    return null;
  }

  @NotNull
  public static String getModuleDir(@NotNull Module module) {
    return PathUtil.getParentPath(module.getModuleFilePath());
  }

  @NotNull
  public static String getStorageRootFromOptions(@NotNull Module module) {
    String moduleRoot = getModuleDir(module);
    String storageRef = module.getOptionValue(CLASSPATH_DIR_OPTION);
    if (storageRef == null) {
      return moduleRoot;
    }

    storageRef = FileUtil.toSystemIndependentName(storageRef);
    return storageRef.charAt(0) == '/' ? storageRef : moduleRoot + '/' + storageRef;
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
      module.clearOption(ClassPathStorageUtil.CLASSPATH_OPTION);
      module.clearOption(CLASSPATH_DIR_OPTION);
    }
    else {
      module.setOption(ClassPathStorageUtil.CLASSPATH_OPTION, storageId);
      module.setOption(CLASSPATH_DIR_OPTION, provider.getContentRoot(model));
    }
  }

  public static void moduleRenamed(Module module, String newName) {
    ClasspathStorageProvider provider = getProvider(ClassPathStorageUtil.getStorageType(module));
    if (provider != null) {
      provider.moduleRenamed(module, newName);
    }
  }

  public static void modulePathChanged(Module module, String path) {
    ClasspathStorageProvider provider = getProvider(ClassPathStorageUtil.getStorageType(module));
    if (provider != null) {
      provider.modulePathChanged(module, path);
    }
  }

  private final class ClasspathSaveSession implements ExternalizationSession, SaveSession {
    @Override
    public void setState(@NotNull Object component, @NotNull String componentName, @NotNull Object state, Storage storageSpec) {
      assert component instanceof ModuleRootManager;
      assert componentName.equals("NewModuleRootManager");
      assert state.getClass() == ModuleRootManagerImpl.ModuleRootManagerState.class;

      try {
        myConverter.setClasspath((ModuleRootManagerImpl)component);
      }
      catch (WriteExternalException e) {
        throw new StateStorageException(e);
      }
      catch (IOException e) {
        throw new StateStorageException(e);
      }
    }

    @Nullable
    @Override
    public SaveSession createSaveSession() {
      return this;
    }

    @Override
    public void save() {
      AccessToken token = WriteAction.start();
      try {
        myConverter.getFileSet().commit();
      }
      catch (IOException e) {
        throw new StateStorageException(e);
      }
      finally {
        token.finish();
      }
    }
  }
}

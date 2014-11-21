/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.ProjectBundle;
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
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ClasspathStorage implements StateStorage {
  @NonNls public static final String SPECIAL_STORAGE = "special";

  public static final String DEFAULT_STORAGE_DESCR = ProjectBundle.message("project.roots.classpath.format.default.descr");

  @NonNls public static final String CLASSPATH_DIR_OPTION = JpsProjectLoader.CLASSPATH_DIR_ATTRIBUTE;

  @NonNls private static final String COMPONENT_TAG = "component";
  private final ClasspathStorageProvider.ClasspathConverter myConverter;
  private final TrackingPathMacroSubstitutor myTrackingPathMacroSubstitutor;

  public ClasspathStorage(@NotNull Module module, @NotNull IModuleStore moduleStore) {
    myConverter = getProvider(ClassPathStorageUtil.getStorageType(module)).createConverter(module);
    myTrackingPathMacroSubstitutor = moduleStore.getStateStorageManager().getMacroSubstitutor();

    final VirtualFileTracker virtualFileTracker = ServiceManager.getService(VirtualFileTracker.class);
    if (virtualFileTracker != null) {
      List<VirtualFile> files = new SmartList<VirtualFile>();
      try {
        myConverter.getFileSet().listFiles(files);
        for (VirtualFile file : files) {
          final Listener listener = module.getProject().getMessageBus().syncPublisher(PROJECT_STORAGE_TOPIC);
          virtualFileTracker.addTracker(file.getUrl(), new VirtualFileAdapter() {
            @Override
            public void contentsChanged(@NotNull final VirtualFileEvent event) {
              listener.storageFileChanged(event, ClasspathStorage.this);
            }
          }, true, module);
        }
      }
      catch (UnsupportedOperationException ignored) {
        // UnsupportedStorageProvider doesn't mean any files
      }
    }
  }

  private FileSet getFileSet() {
    return myConverter.getFileSet();
  }

  @Override
  @Nullable
  public <T> T getState(final Object component, @NotNull final String componentName, @NotNull Class<T> stateClass, @Nullable T mergeInto)
    throws StateStorageException {
    assert component instanceof ModuleRootManager;
    assert componentName.equals("NewModuleRootManager");
    assert stateClass == ModuleRootManagerImpl.ModuleRootManagerState.class;

    try {
      Element element = new Element(COMPONENT_TAG);
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
  public void analyzeExternalChangesAndUpdateIfNeed(@NotNull Collection<VirtualFile> changedFiles, @NotNull Set<String> result) {
  }

  @NotNull
  public static ClasspathStorageProvider getProvider(@NotNull String type) {
    for (ClasspathStorageProvider provider : getProviders()) {
      if (type.equals(provider.getID())) {
        return provider;
      }
    }
    throw new RuntimeException("Cannot find provider for " + type);
  }

  @NotNull
  public static List<ClasspathStorageProvider> getProviders() {
    List<ClasspathStorageProvider> list = new ArrayList<ClasspathStorageProvider>();
    list.add(new DefaultStorageProvider());
    ContainerUtil.addAll(list, ClasspathStorageProvider.EXTENSION_POINT_NAME.getExtensions());
    return list;
  }

  @NotNull
  public static String getModuleDir(@NotNull Module module) {
    //noinspection ConstantConditions
    return new File(module.getModuleFilePath()).getParent();
  }

  public static String getStorageRootFromOptions(final Module module) {
    final String moduleRoot = getModuleDir(module);
    final String storageRef = module.getOptionValue(CLASSPATH_DIR_OPTION);
    if (storageRef == null) {
      return moduleRoot;
    }
    else if (FileUtil.isAbsolute(storageRef)) {
      return storageRef;
    }
    else {
      return FileUtil.toSystemIndependentName(new File(moduleRoot, storageRef).getPath());
    }
  }

  public static void setStorageType(@NotNull ModuleRootModel model, @NotNull String storageID) {
    final Module module = model.getModule();
    final String oldStorageType = ClassPathStorageUtil.getStorageType(module);
    if (oldStorageType.equals(storageID)) {
      return;
    }

    getProvider(oldStorageType).detach(module);

    if (storageID.equals(ClassPathStorageUtil.DEFAULT_STORAGE)) {
      module.clearOption(ClassPathStorageUtil.CLASSPATH_OPTION);
      module.clearOption(CLASSPATH_DIR_OPTION);
    }
    else {
      module.setOption(ClassPathStorageUtil.CLASSPATH_OPTION, storageID);
      module.setOption(CLASSPATH_DIR_OPTION, getProvider(storageID).getContentRoot(model));
    }
  }

  public static void moduleRenamed(Module module, String newName) {
    getProvider(ClassPathStorageUtil.getStorageType(module)).moduleRenamed(module, newName);
  }

  public static void modulePathChanged(Module module, String path) {
    getProvider(ClassPathStorageUtil.getStorageType(module)).modulePathChanged(module, path);
  }

  private static class DefaultStorageProvider implements ClasspathStorageProvider {
    @Override
    @NonNls
    public String getID() {
      return ClassPathStorageUtil.DEFAULT_STORAGE;
    }

    @Override
    @Nls
    public String getDescription() {
      return DEFAULT_STORAGE_DESCR;
    }

    @Override
    public void assertCompatible(final ModuleRootModel model) throws ConfigurationException {
    }

    @Override
    public void detach(Module module) {
    }

    @Override
    public void moduleRenamed(Module module, String newName) {
      //do nothing
    }

    @Override
    public ClasspathConverter createConverter(Module module) {
      throw new UnsupportedOperationException(getDescription());
    }

    @Override
    public String getContentRoot(ModuleRootModel model) {
      return null;
    }

    @Override
    public void modulePathChanged(Module module, String path) {
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
        throw new StateStorageException(e.getMessage());
      }
      catch (IOException e) {
        throw new StateStorageException(e.getMessage());
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
        getFileSet().commit();
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

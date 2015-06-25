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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtilRt;
import com.intellij.util.messages.MessageBus;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ModuleStoreImpl extends BaseFileConfigurableStoreImpl implements IModuleStore {
  private static final Logger LOG = Logger.getInstance(ModuleStoreImpl.class);

  private final ModuleImpl myModule;

  @SuppressWarnings({"UnusedDeclaration"})
  public ModuleStoreImpl(@NotNull ModuleImpl module, @NotNull PathMacroManager pathMacroManager) {
    super(pathMacroManager);

    myModule = module;
  }

  @NotNull
  @Override
  protected FileBasedStorage getMainStorage() {
    FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getStateStorage(StoragePathMacros.MODULE_FILE, RoamingType.PER_USER);
    assert storage != null;
    return storage;
  }

  @Override
  protected Project getProject() {
    return myModule.getProject();
  }

  @Override
  public void load() {
    super.load();

    String moduleTypeId = getMainStorageData().myOptions.get(Module.ELEMENT_TYPE);
    myModule.setOption(Module.ELEMENT_TYPE, ModuleTypeManager.getInstance().findByID(moduleTypeId).getId());

    if (ApplicationManager.getApplication().isHeadlessEnvironment() || ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    final TrackingPathMacroSubstitutor substitutor = getStateStorageManager().getMacroSubstitutor();
    if (substitutor != null) {
      final Collection<String> macros = substitutor.getUnknownMacros(null);
      if (!macros.isEmpty()) {
        final Project project = myModule.getProject();
        StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
          @Override
          public void run() {
            StorageUtil.notifyUnknownMacros(substitutor, project, null);
          }
        });
      }
    }
  }

  @Override
  public ModuleFileData getMainStorageData() {
    return (ModuleFileData)super.getMainStorageData();
  }

  static class ModuleFileData extends BaseStorageData {
    private final Map<String, String> myOptions;
    private final Module myModule;

    private boolean dirty = true;

    public ModuleFileData(@NotNull String rootElementName, @NotNull Module module) {
      super(rootElementName);

      myModule = module;
      myOptions = new TreeMap<String, String>();
    }

    @Override
    public boolean isDirty() {
      return dirty;
    }

    private ModuleFileData(@NotNull ModuleFileData storageData) {
      super(storageData);

      myModule = storageData.myModule;
      dirty = storageData.dirty;
      myOptions = new TreeMap<String, String>(storageData.myOptions);
    }

    @Override
    public void load(@NotNull Element rootElement, @Nullable PathMacroSubstitutor pathMacroSubstitutor, boolean intern) {
      super.load(rootElement, pathMacroSubstitutor, intern);

      for (Attribute attribute : rootElement.getAttributes()) {
        if (!attribute.getName().equals(VERSION_OPTION)) {
          myOptions.put(attribute.getName(), attribute.getValue());
        }
      }

      dirty = false;
    }

    @Override
    protected void writeOptions(@NotNull Element root, @NotNull String versionString) {
      if (!myOptions.isEmpty()) {
        for (Map.Entry<String, String> entry : myOptions.entrySet()) {
          root.setAttribute(entry.getKey(), entry.getValue());
        }
      }
      // need be last for compat reasons
      super.writeOptions(root, versionString);

      dirty = false;
    }

    @Override
    public StorageData clone() {
      return new ModuleFileData(this);
    }

    @Nullable
    @Override
    public Set<String> getChangedComponentNames(@NotNull StorageData newStorageData, @Nullable PathMacroSubstitutor substitutor) {
      final ModuleFileData data = (ModuleFileData)newStorageData;
      if (!myOptions.equals(data.myOptions)) {
        return null;
      }
      return super.getChangedComponentNames(newStorageData, substitutor);
    }

    public void setOption(@NotNull String optionName, @NotNull String optionValue) {
      if (!optionValue.equals(myOptions.put(optionName, optionValue))) {
        dirty = true;
      }
    }

    public void clearOption(@NotNull String optionName) {
      if (myOptions.remove(optionName) != null) {
        dirty = true;
      }
    }

    @Nullable
    public String getOptionValue(@NotNull String optionName) {
      return myOptions.get(optionName);
    }
  }

  @Override
  public void setModuleFilePath(@NotNull String filePath) {
    final String path = filePath.replace(File.separatorChar, '/');
    LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    final StateStorageManager storageManager = getStateStorageManager();
    storageManager.clearStateStorage(StoragePathMacros.MODULE_FILE);
    storageManager.addMacro(StoragePathMacros.MODULE_FILE, path);
  }

  @Override
  @Nullable
  public VirtualFile getModuleFile() {
    return getMainStorage().getVirtualFile();
  }

  @Override
  @NotNull
  public String getModuleFilePath() {
    return getMainStorage().getFilePath();
  }

  @Override
  @NotNull
  public String getModuleFileName() {
    return PathUtilRt.getFileName(getMainStorage().getFilePath());
  }

  @Override
  public void setOption(@NotNull String optionName, @NotNull String optionValue) {
    try {
      getMainStorageData().setOption(optionName,  optionValue);
    }
    catch (StateStorageException e) {
      LOG.error(e);
    }
  }

  @Override
  public void clearOption(@NotNull String optionName) {
    try {
      getMainStorageData().clearOption(optionName);
    }
    catch (StateStorageException e) {
      LOG.error(e);
    }
  }

  @Override
  public String getOptionValue(@NotNull String optionName) {
    try {
      return getMainStorageData().getOptionValue(optionName);
    }
    catch (StateStorageException e) {
      LOG.error(e);
      return null;
    }
  }

  @Override
  protected boolean optimizeTestLoading() {
    return ((ProjectEx)myModule.getProject()).isOptimiseTestLoadSpeed();
  }

  @NotNull
  @Override
  protected MessageBus getMessageBus() {
    return myModule.getMessageBus();
  }

  @NotNull
  @Override
  protected StateStorageManager createStateStorageManager() {
    return new ModuleStateStorageManager(myPathMacroManager.createTrackingSubstitutor(), myModule);
  }
}

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

package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ModuleStoreImpl extends BaseFileConfigurableStoreImpl implements IModuleStore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.ModuleStoreImpl");
  @NonNls private static final String MODULE_FILE_MACRO = "MODULE_FILE";

  private final ModuleImpl myModule;

  public static final String DEFAULT_STATE_STORAGE = "$" + MODULE_FILE_MACRO + "$";


  @SuppressWarnings({"UnusedDeclaration"})
  public ModuleStoreImpl(final ComponentManagerImpl componentManager, final ModuleImpl module) {
    super(componentManager);
    myModule = module;
  }

  protected XmlElementStorage getMainStorage() {
    final XmlElementStorage storage = (XmlElementStorage)getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE);
    assert storage != null;
    return storage;
  }

  @Override
  protected Project getProject() {
    return myModule.getProject();
  }

  @Override
  public void load() throws IOException, StateStorage.StateStorageException {
    super.load();

    final ModuleFileData storageData = getMainStorageData();
    final String moduleTypeId = storageData.myOptions.get(ModuleImpl.ELEMENT_TYPE);
    myModule.setModuleType(ModuleTypeManager.getInstance().findByID(moduleTypeId));

    if (ApplicationManager.getApplication().isHeadlessEnvironment() || ApplicationManager.getApplication().isUnitTestMode()) return;

    final TrackingPathMacroSubstitutor substitutor = getStateStorageManager().getMacroSubstitutor();
    if (substitutor != null) {
      final Collection<String> macros = substitutor.getUnknownMacros(null);
      if (!macros.isEmpty()) {
        final Project project = myModule.getProject();

          StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
            public void run() {
              StorageUtil.notifyUnknownMacros(substitutor, project, null);
            }
          });
      }
    }
  }

  public ModuleFileData getMainStorageData() throws StateStorage.StateStorageException {
    return (ModuleFileData)super.getMainStorageData();
  }

  static class ModuleFileData extends BaseStorageData {
    private final Map<String, String> myOptions;
    private final Module myModule;

    public ModuleFileData(final String rootElementName, Module module) {
      super(rootElementName);
      myModule = module;
      myOptions = new TreeMap<String, String>();
    }

    protected ModuleFileData(final ModuleFileData storageData) {
      super(storageData);

      myOptions = new TreeMap<String, String>(storageData.myOptions);
      myModule = storageData.myModule;
    }

    protected void load(@NotNull final Element rootElement) throws IOException {
      super.load(rootElement);

      final List attributes = rootElement.getAttributes();
      for (Object attribute : attributes) {
        final Attribute attr = (Attribute)attribute;
        myOptions.put(attr.getName(), attr.getValue());
      }
    }

    @Override
    public boolean isEmpty() {
      return super.isEmpty() && myOptions.isEmpty();
    }

    @NotNull
    protected Element save() {
      final Element root = super.save();

      myOptions.put(VERSION_OPTION, Integer.toString(myVersion));
      Set<String> options = myOptions.keySet();
      for (String option : options) {
        root.setAttribute(option, myOptions.get(option));
      }

      //need be last for compat reasons
      root.removeAttribute(VERSION_OPTION);
      root.setAttribute(VERSION_OPTION, Integer.toString(myVersion));

      return root;
    }

    public XmlElementStorage.StorageData clone() {
      return new ModuleFileData(this);
    }

    protected int computeHash() {
      return super.computeHash()*31 + myOptions.hashCode();
    }

    @Nullable
    public Set<String> getDifference(final XmlElementStorage.StorageData storageData, PathMacroSubstitutor substitutor) {
      final ModuleFileData data = (ModuleFileData)storageData;
      if (!myOptions.equals(data.myOptions)) return null;
      return super.getDifference(storageData, substitutor);
    }

    public void setOption(final String optionName, final String optionValue) {
      clearHash();
      myOptions.put(optionName, optionValue);
    }

    public void clearOption(final String optionName) {
      clearHash();
      myOptions.remove(optionName);
    }

    public String getOptionValue(final String optionName) {
      return myOptions.get(optionName);
    }
  }

  public void setModuleFilePath(@NotNull final String filePath) {
    final String path = filePath.replace(File.separatorChar, '/');
    LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    final StateStorageManager storageManager = getStateStorageManager();
    storageManager.clearStateStorage(DEFAULT_STATE_STORAGE);
    storageManager.addMacro(MODULE_FILE_MACRO, path);
  }

  @Nullable
  public VirtualFile getModuleFile() {
    final FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE);
    assert storage != null;
    return storage.getVirtualFile();
  }

  @NotNull
  public String getModuleFilePath() {
    final FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE);
    assert storage != null;
    return storage.getFilePath();
  }

  @NotNull
  public String getModuleFileName() {
    final FileBasedStorage storage = (FileBasedStorage)getStateStorageManager().getFileStateStorage(DEFAULT_STATE_STORAGE);
    assert storage != null;
    return storage.getFileName();
  }

  public void setOption(final String optionName, final String optionValue) {
    try {
      getMainStorageData().setOption(optionName,  optionValue);
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error(e);
    }
  }

  public void clearOption(final String optionName) {
    try {
      getMainStorageData().clearOption(optionName);
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error(e);
    }
  }

  public String getOptionValue(final String optionName) {
    try {
      return getMainStorageData().getOptionValue(optionName);
    }
    catch (StateStorage.StateStorageException e) {
      LOG.error(e);
      return null;
    }
  }

  @Override
  protected boolean optimizeTestLoading() {
    return ((ProjectEx)myModule.getProject()).isOptimiseTestLoadSpeed();
  }

  protected StateStorageManager createStateStorageManager() {
    return new ModuleStateStorageManager(PathMacroManager.getInstance(getComponentManager()).createTrackingSubstitutor(), myModule);
  }
}

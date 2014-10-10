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

import com.intellij.openapi.components.StateStorage.SaveSession;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.components.store.ComponentSaveSession;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class ProjectWithModulesStoreImpl extends ProjectStoreImpl {
  public ProjectWithModulesStoreImpl(@NotNull ProjectImpl project) {
    super(project);
  }

  @Override
  protected boolean reinitComponent(@NotNull String componentName, boolean reloadData) {
    if (super.reinitComponent(componentName, reloadData)) {
      return true;
    }

    for (Module module : getPersistentModules()) {
      // we have to reinit all modules for component because we don't know affected module
      ((ModuleStoreImpl)((ModuleImpl)module).getStateStore()).reinitComponent(componentName, reloadData);
    }
    return true;
  }

  @Override
  public TrackingPathMacroSubstitutor[] getSubstitutors() {
    List<TrackingPathMacroSubstitutor> result = new SmartList<TrackingPathMacroSubstitutor>();
    ContainerUtil.addIfNotNull(result, getStateStorageManager().getMacroSubstitutor());

    for (Module module : getPersistentModules()) {
      ContainerUtil.addIfNotNull(result, ((ModuleImpl)module).getStateStore().getStateStorageManager().getMacroSubstitutor());
    }

    return result.toArray(new TrackingPathMacroSubstitutor[result.size()]);
  }

  @Override
  public boolean isReloadPossible(@NotNull Set<String> componentNames) {
    if (!super.isReloadPossible(componentNames)) {
      return false;
    }

    for (Module module : getPersistentModules()) {
      if (!((ModuleImpl)module).getStateStore().isReloadPossible(componentNames)) {
        return false;
      }
    }

    return true;
  }

  @NotNull
  protected Module[] getPersistentModules() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    return moduleManager == null ? Module.EMPTY_ARRAY : moduleManager.getModules();
  }

  @Override
  protected SaveSessionImpl createSaveSession() {
    return new ProjectWithModulesSaveSession();
  }

  private class ProjectWithModulesSaveSession extends ProjectSaveSession {
    final List<ComponentSaveSession> myModuleSaveSessions = new SmartList<ComponentSaveSession>();

    public ProjectWithModulesSaveSession() {
      for (Module module : getPersistentModules()) {
        ContainerUtil.addIfNotNull(myModuleSaveSessions, ((ModuleImpl)module).getStateStore().startSave());
      }
    }

    @Override
    public void finishSave() {
      try {
        Throwable first = null;
        for (ComponentSaveSession moduleSaveSession : myModuleSaveSessions) {
          try {
            moduleSaveSession.finishSave();
          }
          catch (Throwable e) {
            if (first == null) {
              first = e;
            }
          }
        }

        if (first != null) {
          throw new RuntimeException(first);
        }
      }
      finally {
        super.finishSave();
      }
    }

    @Override
    protected void beforeSave(@NotNull List<Pair<SaveSession, VirtualFile>> readonlyFiles) {
      super.beforeSave(readonlyFiles);

      for (ComponentSaveSession moduleSaveSession : myModuleSaveSessions) {
        moduleSaveSession.save(readonlyFiles);
      }
    }
  }
}

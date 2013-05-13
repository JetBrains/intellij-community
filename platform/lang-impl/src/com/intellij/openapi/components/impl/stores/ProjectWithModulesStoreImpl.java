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

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleImpl;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.fs.IFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class ProjectWithModulesStoreImpl extends ProjectStoreImpl {
  public ProjectWithModulesStoreImpl(final ProjectImpl project) {
    super(project);
  }

  @Override
  public void reinitComponents(@NotNull final Set<String> componentNames, final boolean reloadData) {
    super.reinitComponents(componentNames, reloadData);

    for (Module module : getPersistentModules()) {
      ((ModuleImpl)module).getStateStore().reinitComponents(componentNames, reloadData);
    }
  }

  @Override
  public TrackingPathMacroSubstitutor[] getSubstitutors() {
    final List<TrackingPathMacroSubstitutor> result = new ArrayList<TrackingPathMacroSubstitutor>();
    result.add(getStateStorageManager().getMacroSubstitutor());

    for (Module module : getPersistentModules()) {
      result.add(((ModuleImpl)module).getStateStore().getStateStorageManager().getMacroSubstitutor());
    }

    return result.toArray(new TrackingPathMacroSubstitutor[result.size()]);
  }

  @Override
  public boolean isReloadPossible(@NotNull final Set<String> componentNames) {
    if (!super.isReloadPossible(componentNames)) return false;

    for (Module module : getPersistentModules()) {
      if (!((ModuleImpl)module).getStateStore().isReloadPossible(componentNames)) return false;
    }

    return true;
  }

  protected Module[] getPersistentModules() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    if (moduleManager == null) return Module.EMPTY_ARRAY;

    return moduleManager.getModules();
  }

  @Override
  protected SaveSessionImpl createSaveSession() throws StateStorageException {
    return new ProjectWithModulesSaveSession();
  }

  private class ProjectWithModulesSaveSession extends ProjectSaveSession {
    List<SaveSession> myModuleSaveSessions = new ArrayList<SaveSession>();

    public ProjectWithModulesSaveSession() throws StateStorageException {
      try {
        for (Module module : getPersistentModules()) {
          myModuleSaveSessions.add(((ModuleImpl)module).getStateStore().startSave());
        }
      }
      catch (IOException e) {
        throw new StateStorageException(e.getMessage());
      }
    }

    @NotNull
    @Override
    public List<IFile> getAllStorageFiles(final boolean includingSubStructures) {
      final List<IFile> result = super.getAllStorageFiles(includingSubStructures);

      if (includingSubStructures) {
        for (SaveSession moduleSaveSession : myModuleSaveSessions) {
          result.addAll(moduleSaveSession.getAllStorageFiles(true));
        }
      }

      return result;
    }

    @Override
    @Nullable
    public Set<String> analyzeExternalChanges(@NotNull final Set<Pair<VirtualFile,StateStorage>> changedFiles) {
      final Set<String> result = super.analyzeExternalChanges(changedFiles);
      if (result == null) return null;

      for (SaveSession moduleSaveSession : myModuleSaveSessions) {
        final Set<String> s = moduleSaveSession.analyzeExternalChanges(changedFiles);
        if (s == null) return null;
        result.addAll(s);
      }

      return result;
    }

    @Override
    public void finishSave() {
      try {
        Throwable first = null;
        for (SaveSession moduleSaveSession : myModuleSaveSessions) {
          try {
            moduleSaveSession.finishSave();
          }
          catch(Throwable e) {
            if (first == null) first = e;
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
    public void reset() {
      try {
        for (SaveSession moduleSaveSession : myModuleSaveSessions) {
          moduleSaveSession.reset();
        }
      }
      finally {
        super.reset();
      }
    }

    @Override
    protected void beforeSave() throws IOException {
      super.beforeSave();
      for (SaveSession moduleSaveSession : myModuleSaveSessions) {
        moduleSaveSession.save();
      }
    }

    @Override
    protected void collectSubfilesToSave(final List<IFile> result) throws IOException {
      for (SaveSession moduleSaveSession : myModuleSaveSessions) {
        final List<IFile> moduleFiles = moduleSaveSession.getAllStorageFilesToSave(true);
        result.addAll(moduleFiles);
      }
    }
  }
}

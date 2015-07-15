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

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorage.SaveSession;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public class ProjectWithModulesStoreImpl extends ProjectStoreImpl {
  public ProjectWithModulesStoreImpl(@NotNull ProjectImpl project, @NotNull PathMacroManager pathMacroManager) {
    super(project, pathMacroManager);
  }

  @Override
  public boolean reinitComponent(@NotNull String componentName, @NotNull Set<StateStorage> changedStorages) {
    if (super.reinitComponent(componentName, changedStorages)) {
      return true;
    }

    for (Module module : getPersistentModules()) {
      // we have to reinit all modules for component because we don't know affected module
      getComponentStore(module).reinitComponent(componentName, changedStorages);
    }
    return true;
  }

  @NotNull
  private static IComponentStore getComponentStore(@NotNull Module module) {
    return (IComponentStore)module.getPicoContainer().getComponentInstance(IComponentStore.class);
  }

  @NotNull
  @Override
  public TrackingPathMacroSubstitutor[] getSubstitutors() {
    List<TrackingPathMacroSubstitutor> result = new SmartList<TrackingPathMacroSubstitutor>();
    ContainerUtil.addIfNotNull(result, getStateStorageManager().getMacroSubstitutor());

    for (Module module : getPersistentModules()) {
      ContainerUtil.addIfNotNull(result, getComponentStore(module).getStateStorageManager().getMacroSubstitutor());
    }

    return result.toArray(new TrackingPathMacroSubstitutor[result.size()]);
  }

  @Override
  public boolean isReloadPossible(@NotNull Set<String> componentNames) {
    if (!super.isReloadPossible(componentNames)) {
      return false;
    }

    for (Module module : getPersistentModules()) {
      if (!getComponentStore(module).isReloadPossible(componentNames)) {
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
  protected void beforeSave(@NotNull List<Pair<SaveSession, VirtualFile>> readonlyFiles) {
    super.beforeSave(readonlyFiles);

    for (Module module : getPersistentModules()) {
      getComponentStore(module).save(readonlyFiles);
    }
  }
}

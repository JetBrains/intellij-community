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
package com.intellij.configurationStore

import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.StateStorage.SaveSession
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil

public class ProjectWithModulesStoreImpl(project: ProjectImpl, pathMacroManager: PathMacroManager) : ProjectStoreImpl(project, pathMacroManager) {

  override fun reinitComponent(componentName: String, changedStorages: Set<StateStorage>): Boolean {
    if (super.reinitComponent(componentName, changedStorages)) {
      return true
    }

    for (module in getPersistentModules()) {
      // we have to reinit all modules for component because we don't know affected module
      getComponentStore(module).reinitComponent(componentName, changedStorages)
    }
    return true
  }

  private fun getComponentStore(module: Module): IComponentStore {
    return module.getPicoContainer().getComponentInstance(javaClass<IComponentStore>()) as IComponentStore
  }

  override fun getSubstitutors(): Array<TrackingPathMacroSubstitutor> {
    val result = SmartList<TrackingPathMacroSubstitutor>()
    ContainerUtil.addIfNotNull(result, getStateStorageManager().getMacroSubstitutor())

    for (module in getPersistentModules()) {
      ContainerUtil.addIfNotNull(result, getComponentStore(module).getStateStorageManager().getMacroSubstitutor())
    }

    return result.toArray<TrackingPathMacroSubstitutor>(arrayOfNulls<TrackingPathMacroSubstitutor>(result.size()))
  }

  override fun isReloadPossible(componentNames: Set<String>): Boolean {
    if (!super.isReloadPossible(componentNames)) {
      return false
    }

    for (module in getPersistentModules()) {
      if (!getComponentStore(module).isReloadPossible(componentNames)) {
        return false
      }
    }

    return true
  }

  protected fun getPersistentModules(): Array<Module> {
    val moduleManager = ModuleManager.getInstance(myProject)
    return if (moduleManager == null) Module.EMPTY_ARRAY else moduleManager.getModules()
  }

  override protected fun beforeSave(readonlyFiles: List<Pair<SaveSession, VirtualFile>>) {
    super.beforeSave(readonlyFiles)

    for (module in getPersistentModules()) {
      getComponentStore(module).save(readonlyFiles)
    }
  }
}

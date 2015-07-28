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
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx

class ModuleStoreImpl(private val myModule: Module, pathMacroManager: PathMacroManager) : BaseFileConfigurableStoreImpl(pathMacroManager) {
  override val project: Project?
    get() = myModule.getProject()

  override fun optimizeTestLoading() = (myModule.getProject() as ProjectEx).isOptimiseTestLoadSpeed()

  override fun getMessageBus() = myModule.getMessageBus()

  override fun createStorageManager() = ModuleStateStorageManager(pathMacroManager.createTrackingSubstitutor(), myModule)
}
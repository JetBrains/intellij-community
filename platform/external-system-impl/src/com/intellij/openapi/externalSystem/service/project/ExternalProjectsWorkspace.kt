// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.components.*
import com.intellij.openapi.components.StoragePathMacros.CACHE_FILE
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
@State(name = "ExternalProjectsWorkspace", storages = [Storage(CACHE_FILE)])
class ExternalProjectsWorkspace : SimplePersistentStateComponent<ExternalProjectsWorkspace.State>(State()) {

  class State : BaseState() {

    @get:Property(surroundWithTag = false)
    @get:MapAnnotation(
      surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false,
      entryTagName = "module", keyAttributeName = "name", valueAttributeName = "library")
    var librarySubstitutions by map<String, String>()
  }

  @RequiresReadLock
  fun getModifiableModel(modelProvider: IdeModifiableModelsProvider): ModifiableWorkspaceModel {
    if (!Registry.`is`("external.system.substitute.library.dependencies")) {
      return ModifiableWorkspaceModel.NOP
    }
    return ModifiableWorkspaceModelImpl(state, modelProvider)
  }

  companion object {

    @JvmStatic
    fun getInstance(project: Project): ExternalProjectsWorkspace {
      return project.service<ExternalProjectsWorkspace>()
    }
  }
}

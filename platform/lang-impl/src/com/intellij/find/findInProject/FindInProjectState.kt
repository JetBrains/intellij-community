// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findInProject

import com.intellij.find.FindModel
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

class FindInProjectState private constructor(private val storage: PropertiesComponent) {

  fun load(model: FindModel) {
    val customScopeName = this.customScopeName
    model.isCustomScope = isCustomScope && customScopeName != null
    model.isProjectScope = isProjectScope
    model.directoryName = directoryName
    model.moduleName = moduleName
    model.customScopeName = customScopeName
  }

  fun save(model: FindModel) {
    isCustomScope = model.isCustomScope
    isProjectScope = model.isProjectScope
    if (model.directoryName != null) {
      directoryName = model.directoryName
      moduleName = null
    }
    else if (model.moduleName != null) {
      directoryName = null
      moduleName = model.moduleName
    }
    else {
      directoryName = null
      moduleName = null
    }
    customScopeName = model.customScopeName
  }

  private var isCustomScope: Boolean
    get() = storage.getBoolean("FindInProjectState.isCustomScope", false)
    set(value) {
      storage.setValue("FindInProjectState.isCustomScope", value, false)
    }

  private var isProjectScope: Boolean
    get() = storage.getBoolean("FindInProjectState.isProjectScope", true)
    set(value) {
      storage.setValue("FindInProjectState.isProjectScope", value, true)
    }

  private var directoryName: String?
    get() = storage.getValue("FindInProjectState.directoryName")
    set(value) {
      storage.setValue("FindInProjectState.directoryName", value, null)
    }

  private var moduleName: String?
    get() = storage.getValue("FindInProjectState.moduleName")
    set(value) {
      storage.setValue("FindInProjectState.moduleName", value, null)
    }

  @get:Nls
  private var customScopeName: String?
    get() = storage.getValue("FindInProjectState.customScopeName") // NON-NLS
    set(@Nls value) {
      storage.setValue("FindInProjectState.customScopeName", value, null)
    }

  companion object {
    @JvmStatic fun getInstance(project: Project) = FindInProjectState(PropertiesComponent.getInstance(project))
  }

}

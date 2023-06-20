// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findInProject

import com.intellij.find.FindModel
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.annotations.Nls

@Service(Service.Level.PROJECT)
@State(name = "FindInProjectScope", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
internal class FindInProjectScopeService : PersistentStateComponent<FindInProjectScopeService> {

  fun load(model: FindModel) {
    val customScopeName = this.customScopeName
    model.isCustomScope = isCustomScope && customScopeName != null
    model.isProjectScope = isProjectScope
    model.directoryName = directoryName
    model.moduleName = moduleName
    if (model.isCustomScope) {
      model.customScope = null
      model.customScopeName = customScopeName
    }
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

  @Property
  private var isCustomScope: Boolean = false
  @Property
  private var isProjectScope: Boolean = false
  @Property
  private var directoryName: String? = null
  @Property
  private var moduleName: String? = null
  @Nls
  @Property
  private var customScopeName: String? = null

  override fun getState(): FindInProjectScopeService = this

  override fun loadState(state: FindInProjectScopeService) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    @JvmStatic fun getInstance(project: Project): FindInProjectScopeService = project.service()
  }

}

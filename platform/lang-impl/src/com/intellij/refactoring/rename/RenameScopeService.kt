// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.openapi.components.*
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.psi.search.ProjectScope
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.annotations.Nls

@Service(Service.Level.PROJECT)
@State(name = "RenameScope", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
internal class RenameScopeService : PersistentStateComponent<RenameScopeService> {

  fun load(): @Nls String = (if (isEnabled) scopeName else null) ?: defaultValue()

  fun defaultValue(): @Nls String = ProjectScope.getProjectFilesScopeName()

  fun save(scopeName: @Nls String?) {
    if (isEnabled) {
      this.scopeName = scopeName
    }
  }

  private val isEnabled: Boolean get() = AdvancedSettings.getBoolean("ide.remember.last.search.scope")

  @Nls
  @Property
  private var scopeName: String? = null

  override fun getState(): RenameScopeService = this

  override fun loadState(state: RenameScopeService) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    @JvmStatic fun getInstance(project: Project): RenameScopeService = project.service()
  }

}

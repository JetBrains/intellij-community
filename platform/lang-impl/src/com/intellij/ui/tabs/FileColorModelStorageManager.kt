// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.ui.FileColorManager
import org.jdom.Element

internal abstract class FileColorModelStorageManager(private val project: Project) : PersistentStateComponent<Element> {
  protected abstract val perUser: Boolean

  private fun getFileColorManager() = FileColorManager.getInstance(project) as FileColorManagerImpl

  override fun getState(): Element {
    return getFileColorManager().uninitializedModel.save(!perUser)
  }

  override fun loadState(state: Element) {
    getFileColorManager().uninitializedModel.load(state, !perUser)
  }
}

@State(name = "SharedFileColors", storages = [Storage("fileColors.xml")])
internal class PerTeamFileColorModelStorageManager(project: Project) : FileColorModelStorageManager(project) {
  override val perUser: Boolean
    get() = false
}

@State(name = "FileColors", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class PerUserFileColorModelStorageManager(project: Project) : FileColorModelStorageManager(project) {
  override val perUser: Boolean
    get() = true
}
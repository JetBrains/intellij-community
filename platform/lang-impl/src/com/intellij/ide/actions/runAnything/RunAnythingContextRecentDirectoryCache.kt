// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.XCollection

@State(name = "RunAnythingContextRecentDirectoryCache", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class RunAnythingContextRecentDirectoryCache : PersistentStateComponent<RunAnythingContextRecentDirectoryCache.State> {
  private val mySettings = State()

  override fun getState(): State {
    return mySettings
  }

  override fun loadState(state: State) {
    XmlSerializerUtil.copyBean(state, mySettings)
  }

  class State {
    @XCollection(elementName = "recentPaths")
    val paths: MutableList<String> = mutableListOf()
  }

  companion object {

    fun getInstance(project: Project): RunAnythingContextRecentDirectoryCache {
      return project.getService(RunAnythingContextRecentDirectoryCache::class.java)
    }
  }
}
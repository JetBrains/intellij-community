// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.XCollection

@Service(Service.Level.PROJECT)
@State(name = "RunAnythingContextRecentDirectoryCache", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class RunAnythingContextRecentDirectoryCache : PersistentStateComponent<RunAnythingContextRecentDirectoryCache.State> {
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
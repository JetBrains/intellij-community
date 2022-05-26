// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XCollection
import java.util.*
import java.util.concurrent.ConcurrentHashMap

open class ProblemsViewState : BaseState() {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.getService(ProblemsViewStateManager::class.java).state
  }

  var selectedTabId by string("")
  
  var proportion by property(0.5f)

  var autoscrollToSource by property(false)
  var showPreview by property(false)
  var showToolbar by property(true)

  var groupByToolId by property(false)
  var sortFoldersFirst by property(true)
  var sortBySeverity by property(true)
  var sortByName by property(false)

  @get:XCollection(style = XCollection.Style.v2)
  val hideBySeverity: MutableSet<Int> by property(Collections.newSetFromMap(ConcurrentHashMap())) { it.isEmpty() }
}

@State(name = "ProblemsViewState", storages = [(Storage(value = StoragePathMacros.WORKSPACE_FILE))])
internal class ProblemsViewStateManager : SimplePersistentStateComponent<ProblemsViewState>(ProblemsViewState()) {
  override fun noStateLoaded() {
    state.autoscrollToSource = UISettings.getInstance().state.defaultAutoScrollToSource
  }
}

// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XCollection
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal class ProblemsViewState : BaseState() {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.getService(ProblemsViewStateManager::class.java).state
  }

  var selectedIndex by property(0)
  var proportion by property(0.5f)

  var autoscrollToSource by property(false)
  var showPreview by property(false)
  var showToolbar by property(true)

  var sortFoldersFirst by property(true)
  var sortByGroupId by property(false)
  var sortBySeverity by property(true)
  var sortByName by property(false)

  @get:XCollection(style = XCollection.Style.v2)
  val hideBySeverity: MutableSet<Int> by property(Collections.newSetFromMap(ConcurrentHashMap()), { it.isEmpty() })
}

@State(name = "ProblemsViewState", storages = [(Storage(value = StoragePathMacros.WORKSPACE_FILE))])
internal class ProblemsViewStateManager : SimplePersistentStateComponent<ProblemsViewState>(ProblemsViewState()) {
  override fun noStateLoaded() {
    state.autoscrollToSource = UISettings.instance.state.defaultAutoScrollToSource
  }
}

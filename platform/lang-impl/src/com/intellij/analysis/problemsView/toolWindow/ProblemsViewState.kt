// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XCollection

open class ProblemsViewState : BaseState() {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProblemsViewState = project.getService(ProblemsViewStateManager::class.java).state
  }

  var selectedTabId: String? by string("")
  
  var proportion: Float by property(0.5f)

  var autoscrollToSource: Boolean by property(false)
  var showPreview: Boolean by property(false)
  var showToolbar: Boolean by property(true)

  var groupByToolId: Boolean by property(false)
  var sortFoldersFirst: Boolean by property(true)
  var sortBySeverity: Boolean by property(true)
  var sortByName: Boolean by property(false)

  @get:XCollection(style = XCollection.Style.v2)
  val hideBySeverity: MutableSet<Int> by property(ConcurrentCollectionFactory.createConcurrentSet()) { it.isEmpty() }

  fun removeSeverity(severity: Int): Boolean {
    val changed = hideBySeverity.remove(severity)
    if (changed) incrementModificationCount()
    return changed
  }

  fun addSeverity(severity: Int): Boolean {
    val changed = hideBySeverity.add(severity)
    if (changed) incrementModificationCount()
    return changed
  }

  fun removeAllSeverities(severities: Collection<Int>): Boolean {
    val changed = hideBySeverity.removeAll(severities)
    if (changed) incrementModificationCount()
    return changed
  }

  fun addAllSeverities(severities: Collection<Int>): Boolean {
    val changed = hideBySeverity.addAll(severities)
    if (changed) incrementModificationCount()
    return changed
  }
}

@Service(Service.Level.PROJECT)
@State(name = "ProblemsViewState", storages = [(Storage(value = StoragePathMacros.WORKSPACE_FILE))])
internal class ProblemsViewStateManager : SimplePersistentStateComponent<ProblemsViewState>(ProblemsViewState()) {
  override fun noStateLoaded() {
    state.autoscrollToSource = UISettings.getInstance().state.defaultAutoScrollToSource
  }
}

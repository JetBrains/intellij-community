// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.ide.projectView.ProjectViewSettings
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "ProjectViewState", storages = [(Storage(value = StoragePathMacros.WORKSPACE_FILE))])
class ProjectViewState : PersistentStateComponent<ProjectViewState> {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectViewState = project.service()

    @JvmStatic
    fun getDefaultInstance(): ProjectViewState = getInstance(ProjectManager.getInstance().defaultProject)
  }

  var abbreviatePackageNames: Boolean = ProjectViewSettings.Immutable.DEFAULT.isAbbreviatePackageNames
  var autoscrollFromSource: Boolean = false
  var autoscrollToSource: Boolean = UISettings.getInstance().state.defaultAutoScrollToSource
  var openDirectoriesWithSingleClick: Boolean = false
  var compactDirectories: Boolean = ProjectViewSettings.Immutable.DEFAULT.isCompactDirectories
  var flattenModules: Boolean = ProjectViewSettings.Immutable.DEFAULT.isFlattenModules
  var flattenPackages: Boolean = ProjectViewSettings.Immutable.DEFAULT.isFlattenPackages
  var foldersAlwaysOnTop: Boolean = ProjectViewSettings.Immutable.DEFAULT.isFoldersAlwaysOnTop
  var hideEmptyMiddlePackages: Boolean = ProjectViewSettings.Immutable.DEFAULT.isHideEmptyMiddlePackages
  var manualOrder: Boolean = false
  var showExcludedFiles: Boolean = ProjectViewSettings.Immutable.DEFAULT.isShowExcludedFiles
  var showLibraryContents: Boolean = ProjectViewSettings.Immutable.DEFAULT.isShowLibraryContents
  var showMembers: Boolean = ProjectViewSettings.Immutable.DEFAULT.isShowMembers
  var showModules: Boolean = ProjectViewSettings.Immutable.DEFAULT.isShowModules
  var showScratchesAndConsoles: Boolean = ProjectViewSettings.Immutable.DEFAULT.isShowScratchesAndConsoles
  var showURL: Boolean = ProjectViewSettings.Immutable.DEFAULT.isShowURL
  var showVisibilityIcons: Boolean = ProjectViewSettings.Immutable.DEFAULT.isShowVisibilityIcons
  var useFileNestingRules: Boolean = ProjectViewSettings.Immutable.DEFAULT.isUseFileNestingRules
  @get:ReportValue
  var sortKey: NodeSortKey = ProjectViewSettings.Immutable.DEFAULT.sortKey

  @Deprecated(
    "More sorting options are available now, use sortKey instead",
    replaceWith = ReplaceWith("sortKey == NodeSortKey.BY_TYPE")
  )
  @get:SkipReportingStatistics
  var sortByType: Boolean
    get() = sortKey == NodeSortKey.BY_TYPE
    set(value) {
      sortKey = if (value) NodeSortKey.BY_TYPE else NodeSortKey.BY_NAME
    }

  override fun noStateLoaded() {
    val application = getApplication()
    if (application == null || application.isUnitTestMode) return
    // for backward compatibility
    abbreviatePackageNames = ProjectViewSharedSettings.instance.abbreviatePackages
    autoscrollFromSource = ProjectViewSharedSettings.instance.autoscrollFromSource
    autoscrollToSource = ProjectViewSharedSettings.instance.autoscrollToSource
    openDirectoriesWithSingleClick = ProjectViewSharedSettings.instance.openDirectoriesWithSingleClick
    compactDirectories = ProjectViewSharedSettings.instance.compactDirectories
    flattenModules = ProjectViewSharedSettings.instance.flattenModules
    flattenPackages = ProjectViewSharedSettings.instance.flattenPackages
    foldersAlwaysOnTop = ProjectViewSharedSettings.instance.foldersAlwaysOnTop
    hideEmptyMiddlePackages = ProjectViewSharedSettings.instance.hideEmptyPackages
    manualOrder = ProjectViewSharedSettings.instance.manualOrder
    showExcludedFiles = ProjectViewSharedSettings.instance.showExcludedFiles
    showLibraryContents = ProjectViewSharedSettings.instance.showLibraryContents
    showMembers = ProjectViewSharedSettings.instance.showMembers
    showModules = ProjectViewSharedSettings.instance.showModules
    showScratchesAndConsoles = ProjectViewSharedSettings.instance.showScratchesAndConsoles
    showURL = Registry.`is`("project.tree.structure.show.url")
    showVisibilityIcons = ProjectViewSharedSettings.instance.showVisibilityIcons
    sortKey = ProjectViewSharedSettings.instance.sortKey
  }

  override fun loadState(state: ProjectViewState) {
    XmlSerializerUtil.copyBean(state, this)
  }

  override fun getState(): ProjectViewState {
    return this
  }
}

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
    fun getDefaultInstance(): ProjectViewState = ProjectManager.getInstance().defaultProject.service()
  }

  var abbreviatePackageNames = ProjectViewSettings.Immutable.DEFAULT.isAbbreviatePackageNames
  var autoscrollFromSource = false
  var autoscrollToSource = UISettings.getInstance().state.defaultAutoScrollToSource
  var openDirectoriesWithSingleClick = false
  var compactDirectories = ProjectViewSettings.Immutable.DEFAULT.isCompactDirectories
  var flattenModules = ProjectViewSettings.Immutable.DEFAULT.isFlattenModules
  var flattenPackages = ProjectViewSettings.Immutable.DEFAULT.isFlattenPackages
  var foldersAlwaysOnTop = ProjectViewSettings.Immutable.DEFAULT.isFoldersAlwaysOnTop
  var hideEmptyMiddlePackages = ProjectViewSettings.Immutable.DEFAULT.isHideEmptyMiddlePackages
  var manualOrder = false
  var showExcludedFiles = ProjectViewSettings.Immutable.DEFAULT.isShowExcludedFiles
  var showLibraryContents = ProjectViewSettings.Immutable.DEFAULT.isShowLibraryContents
  var showMembers = ProjectViewSettings.Immutable.DEFAULT.isShowMembers
  var showModules = ProjectViewSettings.Immutable.DEFAULT.isShowModules
  var showScratchesAndConsoles = ProjectViewSettings.Immutable.DEFAULT.isShowScratchesAndConsoles
  var showURL = ProjectViewSettings.Immutable.DEFAULT.isShowURL
  var showVisibilityIcons = ProjectViewSettings.Immutable.DEFAULT.isShowVisibilityIcons
  var useFileNestingRules = ProjectViewSettings.Immutable.DEFAULT.isUseFileNestingRules
  var sortKey = ProjectViewSettings.Immutable.DEFAULT.sortKey

  @Deprecated(
    "More sorting options are available now, use sortKey instead",
    replaceWith = ReplaceWith("sortKey == NodeSortKey.BY_TYPE")
  )
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

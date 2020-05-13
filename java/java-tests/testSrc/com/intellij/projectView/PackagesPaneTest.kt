// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView

import com.intellij.ide.projectView.ProjectViewSettings
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ProjectViewState
import com.intellij.ide.util.treeView.NodeOptions
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generateInVirtualTempDir

class PackagesPaneTest : AbstractProjectViewTest() {
  override fun setUp() {
    super.setUp()
    selectPackagesPane()
  }

  fun `test default settings`() {
    val settings = currentSettings // mutable delegate
    val state = ProjectViewState.getInstance(project)

    // ProjectViewSettings

    val defaultShowExcludedFiles = ProjectViewSettings.Immutable.DEFAULT.isShowExcludedFiles
    assertEquals(false, settings.isShowExcludedFiles) // not supported in the Packages view
    state.showExcludedFiles = !defaultShowExcludedFiles
    assertEquals(false, settings.isShowExcludedFiles) // not supported in the Packages view

    val defaultShowVisibilityIcons = ProjectViewSettings.Immutable.DEFAULT.isShowVisibilityIcons
    assertEquals(defaultShowVisibilityIcons, settings.isShowVisibilityIcons)
    state.showVisibilityIcons = !defaultShowVisibilityIcons
    assertEquals(!defaultShowVisibilityIcons, settings.isShowVisibilityIcons)

    val defaultUseFileNestingRules = ProjectViewSettings.Immutable.DEFAULT.isUseFileNestingRules
    assertEquals(false, settings.isUseFileNestingRules) // not supported in the Packages view
    state.useFileNestingRules = !defaultUseFileNestingRules
    assertEquals(false, settings.isUseFileNestingRules) // not supported in the Packages view

    // ViewSettings

    val defaultFoldersAlwaysOnTop = ViewSettings.Immutable.DEFAULT.isFoldersAlwaysOnTop
    assertEquals(defaultFoldersAlwaysOnTop, settings.isFoldersAlwaysOnTop)
    state.foldersAlwaysOnTop = !defaultFoldersAlwaysOnTop
    assertEquals(!defaultFoldersAlwaysOnTop, settings.isFoldersAlwaysOnTop)

    val defaultShowMembers = ViewSettings.Immutable.DEFAULT.isShowMembers
    assertEquals(defaultShowMembers, settings.isShowMembers)
    state.showMembers = !defaultShowMembers
    assertEquals(!defaultShowMembers, settings.isShowMembers)

    val defaultStructureView = ViewSettings.Immutable.DEFAULT.isStructureView
    assertEquals(defaultStructureView, settings.isStructureView)

    val defaultShowModules = ViewSettings.Immutable.DEFAULT.isShowModules
    assertEquals(defaultShowModules, settings.isShowModules)
    state.showModules = !defaultShowModules
    assertEquals(!defaultShowModules, settings.isShowModules)

    val defaultFlattenModules = ViewSettings.Immutable.DEFAULT.isFlattenModules
    assertEquals(defaultFlattenModules, settings.isFlattenModules)
    state.flattenModules = !defaultFlattenModules
    assertEquals(false, settings.isFlattenModules) // depends on show modules
    state.showModules = true // to test dependency of FlattenModules and ShowModules
    assertEquals(!defaultFlattenModules, settings.isFlattenModules)

    val defaultShowURL = ViewSettings.Immutable.DEFAULT.isShowURL
    assertEquals(defaultShowURL, settings.isShowURL)
    state.showURL = !defaultShowURL
    assertEquals(defaultShowURL, settings.isShowURL) // TODO: cannot be changed now

    // NodeOptions

    val defaultFlattenPackages = NodeOptions.Immutable.DEFAULT.isFlattenPackages
    assertEquals(defaultFlattenPackages, settings.isFlattenPackages)
    state.flattenPackages = !defaultFlattenPackages
    assertEquals(!defaultFlattenPackages, settings.isFlattenPackages)

    val defaultAbbreviatePackageNames = NodeOptions.Immutable.DEFAULT.isAbbreviatePackageNames
    assertEquals(defaultAbbreviatePackageNames, settings.isAbbreviatePackageNames)
    state.abbreviatePackageNames = !defaultAbbreviatePackageNames
    assertEquals(!defaultAbbreviatePackageNames, settings.isAbbreviatePackageNames)

    val defaultHideEmptyMiddlePackages = NodeOptions.Immutable.DEFAULT.isHideEmptyMiddlePackages
    assertEquals(defaultHideEmptyMiddlePackages, settings.isHideEmptyMiddlePackages)
    state.hideEmptyMiddlePackages = !defaultHideEmptyMiddlePackages
    assertEquals(!defaultHideEmptyMiddlePackages, settings.isHideEmptyMiddlePackages)

    val defaultCompactDirectories = NodeOptions.Immutable.DEFAULT.isCompactDirectories
    assertEquals(false, settings.isCompactDirectories) // not supported in the Packages view
    state.compactDirectories = !defaultCompactDirectories
    assertEquals(false, settings.isCompactDirectories) // not supported in the Packages view

    val defaultShowLibraryContents = NodeOptions.Immutable.DEFAULT.isShowLibraryContents
    assertEquals(defaultShowLibraryContents, settings.isShowLibraryContents)
    state.showLibraryContents = !defaultShowLibraryContents
    assertEquals(!defaultShowLibraryContents, settings.isShowLibraryContents)
  }

  fun `test default settings with modules in project`() {
    val root = directoryContent {
      dir("one") {}
      dir("two") {}
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("qualified.one"), root.findChild("one")!!)
    PsiTestUtil.addContentRoot(createModule("qualified.two"), root.findChild("two")!!)
    waitWhileBusy()

    val settings = currentSettings // mutable delegate
    val state = ProjectViewState.getInstance(project)

    // ProjectViewSettings

    val defaultShowExcludedFiles = ProjectViewSettings.Immutable.DEFAULT.isShowExcludedFiles
    assertEquals(false, settings.isShowExcludedFiles) // not supported in the Packages view
    state.showExcludedFiles = !defaultShowExcludedFiles
    assertEquals(false, settings.isShowExcludedFiles) // not supported in the Packages view

    val defaultShowVisibilityIcons = ProjectViewSettings.Immutable.DEFAULT.isShowVisibilityIcons
    assertEquals(defaultShowVisibilityIcons, settings.isShowVisibilityIcons)
    state.showVisibilityIcons = !defaultShowVisibilityIcons
    assertEquals(!defaultShowVisibilityIcons, settings.isShowVisibilityIcons)

    val defaultUseFileNestingRules = ProjectViewSettings.Immutable.DEFAULT.isUseFileNestingRules
    assertEquals(false, settings.isUseFileNestingRules) // not supported in the Packages view
    state.useFileNestingRules = !defaultUseFileNestingRules
    assertEquals(false, settings.isUseFileNestingRules) // not supported in the Packages view

    // ViewSettings

    val defaultFoldersAlwaysOnTop = ViewSettings.Immutable.DEFAULT.isFoldersAlwaysOnTop
    assertEquals(defaultFoldersAlwaysOnTop, settings.isFoldersAlwaysOnTop)
    state.foldersAlwaysOnTop = !defaultFoldersAlwaysOnTop
    assertEquals(!defaultFoldersAlwaysOnTop, settings.isFoldersAlwaysOnTop)

    val defaultShowMembers = ViewSettings.Immutable.DEFAULT.isShowMembers
    assertEquals(defaultShowMembers, settings.isShowMembers)
    state.showMembers = !defaultShowMembers
    assertEquals(!defaultShowMembers, settings.isShowMembers)

    val defaultStructureView = ViewSettings.Immutable.DEFAULT.isStructureView
    assertEquals(defaultStructureView, settings.isStructureView)

    val defaultShowModules = ViewSettings.Immutable.DEFAULT.isShowModules
    assertEquals(defaultShowModules, settings.isShowModules)
    state.showModules = !defaultShowModules
    assertEquals(!defaultShowModules, settings.isShowModules)

    val defaultFlattenModules = ViewSettings.Immutable.DEFAULT.isFlattenModules
    assertEquals(defaultFlattenModules, settings.isFlattenModules)
    state.flattenModules = !defaultFlattenModules
    assertEquals(false, settings.isFlattenModules) // depends on show modules
    state.showModules = true // to test dependency of FlattenModules and ShowModules
    assertEquals(!defaultFlattenModules, settings.isFlattenModules)

    val defaultShowURL = ViewSettings.Immutable.DEFAULT.isShowURL
    assertEquals(defaultShowURL, settings.isShowURL)
    state.showURL = !defaultShowURL
    assertEquals(defaultShowURL, settings.isShowURL) // TODO: cannot be changed now

    // NodeOptions

    val defaultFlattenPackages = NodeOptions.Immutable.DEFAULT.isFlattenPackages
    assertEquals(defaultFlattenPackages, settings.isFlattenPackages)
    state.flattenPackages = !defaultFlattenPackages
    assertEquals(!defaultFlattenPackages, settings.isFlattenPackages)

    val defaultAbbreviatePackageNames = NodeOptions.Immutable.DEFAULT.isAbbreviatePackageNames
    assertEquals(defaultAbbreviatePackageNames, settings.isAbbreviatePackageNames)
    state.abbreviatePackageNames = !defaultAbbreviatePackageNames
    assertEquals(!defaultAbbreviatePackageNames, settings.isAbbreviatePackageNames)

    val defaultHideEmptyMiddlePackages = NodeOptions.Immutable.DEFAULT.isHideEmptyMiddlePackages
    assertEquals(defaultHideEmptyMiddlePackages, settings.isHideEmptyMiddlePackages)
    state.hideEmptyMiddlePackages = !defaultHideEmptyMiddlePackages
    assertEquals(!defaultHideEmptyMiddlePackages, settings.isHideEmptyMiddlePackages)

    val defaultCompactDirectories = NodeOptions.Immutable.DEFAULT.isCompactDirectories
    assertEquals(false, settings.isCompactDirectories) // not supported in the Project view
    state.compactDirectories = !defaultCompactDirectories
    assertEquals(false, settings.isCompactDirectories) // not supported in the Project view

    val defaultShowLibraryContents = NodeOptions.Immutable.DEFAULT.isShowLibraryContents
    assertEquals(defaultShowLibraryContents, settings.isShowLibraryContents)
    state.showLibraryContents = !defaultShowLibraryContents
    assertEquals(!defaultShowLibraryContents, settings.isShowLibraryContents)
  }
}

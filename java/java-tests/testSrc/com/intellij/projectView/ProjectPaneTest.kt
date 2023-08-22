// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView

import com.intellij.ide.projectView.ProjectViewSettings
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ProjectViewState
import com.intellij.ide.util.treeView.NodeOptions
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generateInVirtualTempDir
import kotlin.test.assertNotEquals

class ProjectPaneTest : AbstractProjectViewTest() {
  override fun setUp() {
    super.setUp()
    selectProjectPane()
  }

  fun `test default settings`() {
    val settings = currentSettings // mutable delegate
    val state = ProjectViewState.getInstance(project)

    // ProjectViewSettings

    val defaultShowExcludedFiles = ProjectViewSettings.Immutable.DEFAULT.isShowExcludedFiles
    assertEquals(defaultShowExcludedFiles, settings.isShowExcludedFiles)
    state.showExcludedFiles = !defaultShowExcludedFiles
    assertEquals(!defaultShowExcludedFiles, settings.isShowExcludedFiles)

    val defaultShowVisibilityIcons = ProjectViewSettings.Immutable.DEFAULT.isShowVisibilityIcons
    assertEquals(defaultShowVisibilityIcons, settings.isShowVisibilityIcons)
    state.showVisibilityIcons = !defaultShowVisibilityIcons
    assertEquals(!defaultShowVisibilityIcons, settings.isShowVisibilityIcons)

    val defaultUseFileNestingRules = ProjectViewSettings.Immutable.DEFAULT.isUseFileNestingRules
    assertEquals(defaultUseFileNestingRules, settings.isUseFileNestingRules)
    state.useFileNestingRules = !defaultUseFileNestingRules
    assertEquals(!defaultUseFileNestingRules, settings.isUseFileNestingRules)

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
    assertEquals(false, settings.isShowModules) // not supported in the Project view
    state.showModules = !defaultShowModules
    assertEquals(false, settings.isShowModules) // not supported in the Project view

    val defaultFlattenModules = ViewSettings.Immutable.DEFAULT.isFlattenModules
    assertEquals(false, settings.isFlattenModules) // no modules in project
    state.flattenModules = !defaultFlattenModules
    assertEquals(false, settings.isFlattenModules) // no modules in project

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
    assertEquals(false, settings.isShowLibraryContents) // not supported in the Project view
    state.showLibraryContents = !defaultShowLibraryContents
    assertEquals(false, settings.isShowLibraryContents) // not supported in the Project view
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
    assertEquals(defaultShowExcludedFiles, settings.isShowExcludedFiles)
    state.showExcludedFiles = !defaultShowExcludedFiles
    assertEquals(!defaultShowExcludedFiles, settings.isShowExcludedFiles)

    val defaultShowVisibilityIcons = ProjectViewSettings.Immutable.DEFAULT.isShowVisibilityIcons
    assertEquals(defaultShowVisibilityIcons, settings.isShowVisibilityIcons)
    state.showVisibilityIcons = !defaultShowVisibilityIcons
    assertEquals(!defaultShowVisibilityIcons, settings.isShowVisibilityIcons)

    val defaultUseFileNestingRules = ProjectViewSettings.Immutable.DEFAULT.isUseFileNestingRules
    assertEquals(defaultUseFileNestingRules, settings.isUseFileNestingRules)
    state.useFileNestingRules = !defaultUseFileNestingRules
    assertEquals(!defaultUseFileNestingRules, settings.isUseFileNestingRules)

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
    assertEquals(false, settings.isShowModules) // not supported in the Project view
    state.showModules = !defaultShowModules
    assertEquals(false, settings.isShowModules) // not supported in the Project view

    val defaultFlattenModules = ViewSettings.Immutable.DEFAULT.isFlattenModules
    assertEquals(defaultFlattenModules, settings.isFlattenModules)
    state.flattenModules = !defaultFlattenModules
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
    assertEquals(false, settings.isShowLibraryContents) // not supported in the Project view
    state.showLibraryContents = !defaultShowLibraryContents
    assertEquals(false, settings.isShowLibraryContents) // not supported in the Project view
  }

  fun `test node collapse on sibling add`() {
    with(ProjectViewState.getInstance(project)) {
      hideEmptyMiddlePackages = false
    }
    val test = createTreeTest()
    val temp = getOrCreateModuleDir(module)
    val root = createChildDirectory(temp, "module")
    PsiTestUtil.addSourceRoot(module, root)

    val parent = createChildDirectory(root, "parent")
    val folder = createChildDirectory(parent, "folder")

    selectFile(folder)
    test.assertStructure(" -PsiDirectory: module\n" +
                         "  -PsiDirectory: parent\n" +
                         "   PsiDirectory: folder\n" +
                         " +External Libraries\n")

    selectFile(createChildDirectory(folder, "child"))
    test.assertStructure(" -PsiDirectory: module\n" +
                         "  -PsiDirectory: parent\n" +
                         "   -PsiDirectory: folder\n" +
                         "    PsiDirectory: child\n" +
                         " +External Libraries\n")

    selectFile(createChildDirectory(parent, "sibling"))
    test.assertStructure(" -PsiDirectory: module\n" +
                         "  -PsiDirectory: parent\n" +
                         "   -PsiDirectory: folder\n" +
                         "    PsiDirectory: child\n" +
                         "   PsiDirectory: sibling\n" +
                         " +External Libraries\n")
  }

  fun `test file nesting support`() {
    val test = createTreeTest()
    val root = directoryContent {
      dir("src") {
        dir("com") {
          file("test.js")
          file("test.js.map")
        }
      }
    }.generateInVirtualTempDir()
    PsiTestUtil.addSourceRoot(module, root.findChild("src")!!)
    val mapFile = root.findFileByRelativePath("src/com/test.js.map")!!

    setFileNestingRules(".js" to ".js.map")
    selectFile(mapFile)
    test.assertStructure(" -PsiDirectory: src\n" +
                         "  -PsiDirectory: com\n" +
                         "   -test.js\n" +
                         "    test.js.map\n" +
                         " +External Libraries\n")

    val structureBefore = test.toString()
    projectView.setUseFileNestingRules(false)
    selectFile(mapFile)
    test.assertStructure(" -PsiDirectory: src\n" +
                         "  -PsiDirectory: com\n" +
                         "   test.js\n" +
                         "   test.js.map\n" +
                         " +External Libraries\n")

    assertNotEquals(structureBefore, test.toString())
  }
}

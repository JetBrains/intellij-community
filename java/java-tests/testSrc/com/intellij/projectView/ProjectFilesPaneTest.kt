// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewSettings
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.ProjectViewState
import com.intellij.ide.util.treeView.NodeOptions
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generateInVirtualTempDir
import kotlin.test.assertNotEquals

class ProjectFilesPaneTest : AbstractProjectViewTest() {
  override fun setUp() {
    super.setUp()
    selectProjectFilesPane()
  }

  private fun allowed(any: Any?): Boolean {
    val node = any as? ProjectViewNode<*> ?: return true
    val file = node.virtualFile ?: return true
    return ProjectFileIndex.getInstance(project).isInContent(file)
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
    assertEquals(false, settings.isAbbreviatePackageNames) // not supported in the Scope view
    state.abbreviatePackageNames = !defaultAbbreviatePackageNames
    assertEquals(false, settings.isAbbreviatePackageNames) // not supported in the Scope view

    val defaultHideEmptyMiddlePackages = NodeOptions.Immutable.DEFAULT.isHideEmptyMiddlePackages
    assertEquals(defaultHideEmptyMiddlePackages, settings.isHideEmptyMiddlePackages)
    state.hideEmptyMiddlePackages = !defaultHideEmptyMiddlePackages
    assertEquals(!defaultHideEmptyMiddlePackages, settings.isHideEmptyMiddlePackages)

    val defaultCompactDirectories = NodeOptions.Immutable.DEFAULT.isCompactDirectories
    assertEquals(defaultCompactDirectories, settings.isCompactDirectories)
    state.compactDirectories = !defaultCompactDirectories
    assertEquals(!defaultCompactDirectories, settings.isCompactDirectories)

    val defaultShowLibraryContents = NodeOptions.Immutable.DEFAULT.isShowLibraryContents
    assertEquals(false, settings.isShowLibraryContents) // not supported in the Scope view
    state.showLibraryContents = !defaultShowLibraryContents
    assertEquals(false, settings.isShowLibraryContents) // not supported in the Scope view
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
    assertEquals(false, settings.isAbbreviatePackageNames) // not supported in the Scope view
    state.abbreviatePackageNames = !defaultAbbreviatePackageNames
    assertEquals(false, settings.isAbbreviatePackageNames) // not supported in the Scope view

    val defaultHideEmptyMiddlePackages = NodeOptions.Immutable.DEFAULT.isHideEmptyMiddlePackages
    assertEquals(defaultHideEmptyMiddlePackages, settings.isHideEmptyMiddlePackages)
    state.hideEmptyMiddlePackages = !defaultHideEmptyMiddlePackages
    assertEquals(!defaultHideEmptyMiddlePackages, settings.isHideEmptyMiddlePackages)

    val defaultCompactDirectories = NodeOptions.Immutable.DEFAULT.isCompactDirectories
    assertEquals(defaultCompactDirectories, settings.isCompactDirectories)
    state.compactDirectories = !defaultCompactDirectories
    assertEquals(!defaultCompactDirectories, settings.isCompactDirectories)

    val defaultShowLibraryContents = NodeOptions.Immutable.DEFAULT.isShowLibraryContents
    assertEquals(false, settings.isShowLibraryContents) // not supported in the Scope view
    state.showLibraryContents = !defaultShowLibraryContents
    assertEquals(false, settings.isShowLibraryContents) // not supported in the Scope view
  }

  fun `test node collapse on sibling add`() {
    with(ProjectViewState.getInstance(project)) {
      showExcludedFiles = false
      showModules = false
    }
    val test = createTreeTest().setFilter { allowed(it.lastPathComponent) }
    val temp = getOrCreateModuleDir(module)
    val root = createChildDirectory(temp, "module")
    PsiTestUtil.addSourceRoot(module, root)

    val parent = createChildDirectory(root, "parent")
    val folder = createChildDirectory(parent, "folder")

    selectFile(folder)
    test.assertStructure("  -module\n" +
                         "   -parent\n" +
                         "    folder\n")

    selectFile(createChildDirectory(folder, "child"))
    test.assertStructure("  -module\n" +
                         "   -parent\n" +
                         "    -folder\n" +
                         "     child\n")

    selectFile(createChildDirectory(parent, "sibling"))
    test.assertStructure("  -module\n" +
                         "   -parent\n" +
                         "    -folder\n" +
                         "     child\n" +
                         "    sibling\n")
  }

  fun `test file nesting support`() {
    with(ProjectViewState.getInstance(project)) {
      showModules = false
    }
    val test = createTreeTest().setFilter { allowed(it.lastPathComponent) }
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
    test.assertStructure("   -src\n" +
                         "    -com\n" +
                         "     -test.js\n" +
                         "      test.js.map\n")

    val structureBefore = test.toString()
    projectView.setUseFileNestingRules(false)
    selectFile(mapFile)
    test.assertStructure("   -src\n" +
                         "    -com\n" +
                         "     test.js\n" +
                         "     test.js.map\n")

    assertNotEquals(structureBefore, test.toString())
  }
}

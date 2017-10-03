// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectView

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.ui.Queryable
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generateInVirtualTempDir

/**
 * @author nik
 */
class ProjectViewStructureTest : BaseProjectViewTestCase() {
  init {
    myPrintInfo = Queryable.PrintInfo()
  }
  
  fun `test unloaded modules`() {
    val root = directoryContent {
      dir("loaded") {
        dir("unloaded-inner") {
          dir("subdir") { }
          file("y.txt")
        }
      }
      dir("unloaded") {
        dir("loaded-inner") {
          dir("subdir") { }
          file("z.txt")
        }
      }
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("loaded"), root.findChild("loaded"))
    PsiTestUtil.addContentRoot(createModule("unloaded-inner"), root.findFileByRelativePath("loaded/unloaded-inner"))
    PsiTestUtil.addContentRoot(createModule("unloaded"), root.findChild("unloaded"))
    PsiTestUtil.addContentRoot(createModule("loaded-inner"), root.findFileByRelativePath("unloaded/loaded-inner"))
    myStructure.isShowLibraryContents = false
    val expected = """
          |Project
          | loaded
          |  unloaded-inner
          |   subdir
          |   y.txt
          | loaded-inner.iml
          | loaded.iml
          | test unloaded modules.iml
          | unloaded
          |  loaded-inner
          |   subdir
          |   z.txt
          | unloaded-inner.iml
          | unloaded.iml
          |
          """.trimMargin()
    assertStructureEqual(expected)

    ModuleManager.getInstance(myProject).setUnloadedModules(listOf("unloaded", "unloaded-inner"))
    assertStructureEqual(expected)
  }

  fun `test unloaded module with qualified name`() {
    val root = directoryContent {
      dir("unloaded") {
        dir("subdir") { }
        file("y.txt")
      }
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("foo.bar.unloaded"), root.findChild("unloaded"))

    myStructure.isShowLibraryContents = false
    val expected = """
          |Project
          | Group: foo
          |  Group: bar
          |   unloaded
          |    subdir
          |    y.txt
          | foo.bar.unloaded.iml
          | test unloaded module with qualified name.iml
          |
          """.trimMargin()
    assertStructureEqual(expected)

    ModuleManager.getInstance(myProject).setUnloadedModules(listOf("unloaded"))
    assertStructureEqual(expected)
  }

  override fun getTestPath() = null
}
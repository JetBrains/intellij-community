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
class ModulesInProjectViewTest : BaseProjectViewTestCase() {
  init {
    myPrintInfo = Queryable.PrintInfo()
  }

  override fun setUp() {
    super.setUp()
    myStructure.isShowLibraryContents = false
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
        dir("subdir") {}
        file("y.txt")
      }
      dir("unloaded2") {
        dir("subdir") {}
      }
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("foo.bar.unloaded"), root.findChild("unloaded"))
    PsiTestUtil.addContentRoot(createModule("unloaded2"), root.findChild("unloaded2"))

    val expected = """
          |Project
          | Group: foo.bar
          |  unloaded
          |   subdir
          |   y.txt
          | foo.bar.unloaded.iml
          | test unloaded module with qualified name.iml
          | unloaded2
          |  subdir
          | unloaded2.iml
          |
          """.trimMargin()
    assertStructureEqual(expected)

    ModuleManager.getInstance(myProject).setUnloadedModules(listOf("unloaded"))
    assertStructureEqual(expected)
  }

  fun `test do not show parent groups for single module`() {
    val root = directoryContent {
      dir("module") {
        dir("subdir") {}
      }
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("foo.bar.module"), root.findChild("module"))
    assertStructureEqual("""
          |Project
          | foo.bar.module.iml
          | module
          |  subdir
          | test do not show parent groups for single module.iml
          |
          """.trimMargin())
  }

  fun `test flatten modules option`() {
    val root = directoryContent {
      dir("module1") {}
      dir("module2") {}
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("foo.bar.module1"), root.findChild("module1"))
    PsiTestUtil.addContentRoot(createModule("foo.bar.module2"), root.findChild("module2"))
    myStructure.isFlattenModules = true
    assertStructureEqual("""
          |Project
          | foo.bar.module1.iml
          | foo.bar.module2.iml
          | module1
          | module2
          | test flatten modules option.iml
          |
          """.trimMargin())
  }

  fun `test do not show groups duplicating module names`() {
    val root = directoryContent {
      dir("foo") {}
      dir("foo.bar") {}
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("xxx.foo"), root.findChild("foo"))
    PsiTestUtil.addContentRoot(createModule("xxx.foo.bar"), root.findChild("foo.bar"))
    assertStructureEqual("""
          |Project
          | Group: xxx
          |  foo
          |  foo.bar
          | test do not show groups duplicating module names.iml
          | xxx.foo.bar.iml
          | xxx.foo.iml
          |
          """.trimMargin())
  }

  fun `test modules with common parent group`() {
    val root = directoryContent {
      dir("module1") {
        dir("subdir") {}
      }
      dir("module2") {
        dir("subdir") {}
      }
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("foo.bar.module1"), root.findChild("module1"))
    PsiTestUtil.addContentRoot(createModule("foo.baz.module2"), root.findChild("module2"))
    assertStructureEqual("""
          |Project
          | Group: foo
          |  Group: bar
          |   module1
          |    subdir
          |  Group: baz
          |   module2
          |    subdir
          | foo.bar.module1.iml
          | foo.baz.module2.iml
          | test modules with common parent group.iml
          |
          """.trimMargin())
  }

  override fun getTestPath() = null
}
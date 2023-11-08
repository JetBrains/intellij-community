// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.projectView

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.projectView.impl.PackageViewPane
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.ui.Queryable
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.project.stateStore
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generateInVirtualTempDir

abstract class ModulesInProjectViewTestCase : BaseProjectViewTestCase() {
  init {
    myPrintInfo = Queryable.PrintInfo()
  }

  override fun setUp() {
    super.setUp()
    myStructure.isShowLibraryContents = false
  }

  override fun doCreateRealModule(moduleName: String): Module {
    return WriteAction.computeAndWait<Module, RuntimeException> {
      /* iml files are created under .idea directory to ensure that they won't affect expected structure of Project View;
         this is needed to ensure that tests work the same way under the old project model and under workspace model where all modules
         are saved when a single module is unloaded */
      val imlPath = project.stateStore.projectBasePath.resolve(".idea/$moduleName${ModuleFileType.DOT_DEFAULT_EXTENSION}")
      ModuleManager.getInstance(myProject).newModule(imlPath, moduleType.id)
    }
  }

  override fun getTestPath(): String? = null
}

// directory-based project must be used to ensure that .iws/.ipr file won't break the test (they may be created if workspace model is used)
class ModulesInProjectViewTest : ModulesInProjectViewTestCase() {
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
    PsiTestUtil.addContentRoot(createModule("loaded"), root.findChild("loaded")!!)
    PsiTestUtil.addContentRoot(createModule("unloaded-inner"), root.findFileByRelativePath("loaded/unloaded-inner")!!)
    PsiTestUtil.addContentRoot(createModule("unloaded"), root.findChild("unloaded")!!)
    PsiTestUtil.addContentRoot(createModule("loaded-inner"), root.findFileByRelativePath("unloaded/loaded-inner")!!)
    val expected = """
      Project
       loaded
        unloaded-inner
         subdir
         y.txt
       unloaded
        loaded-inner
         subdir
         z.txt

    """.trimIndent()
    assertStructureEqual(expected)

    runWithModalProgressBlocking(myProject, "") {
      ModuleManager.getInstance(myProject).setUnloadedModules(listOf("unloaded", "unloaded-inner"))
    }
    assertStructureEqual("""
      Project
       loaded
        unloaded-inner
         subdir
         y.txt
       unloaded
        loaded-inner
         subdir
         z.txt
    """.trimIndent())
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
    PsiTestUtil.addContentRoot(createModule("foo.bar.unloaded"), root.findChild("unloaded")!!)
    PsiTestUtil.addContentRoot(createModule("unloaded2"), root.findChild("unloaded2")!!)

    val expected = """
      Project
       Group: foo.bar
        unloaded
         subdir
         y.txt
       unloaded2
        subdir
    """.trimIndent()
    assertStructureEqual(expected)

    runWithModalProgressBlocking(myProject, "") {
      ModuleManager.getInstance(myProject).setUnloadedModules(listOf("unloaded"))
    }
    assertStructureEqual(expected)
  }

  fun `test do not show parent groups for single module`() {
    val root = directoryContent {
      dir("module") {
        dir("subdir") {}
      }
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("foo.bar.module"), root.findChild("module")!!)
    assertStructureEqual("""
          |Project
          | module
          |  subdir
          |
          """.trimMargin())
  }

  fun `test flatten modules option`() {
    val root = directoryContent {
      dir("module1") {}
      dir("module2") {}
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("foo.bar.module1"), root.findChild("module1")!!)
    PsiTestUtil.addContentRoot(createModule("foo.bar.module2"), root.findChild("module2")!!)
    myStructure.isFlattenModules = true
    assertStructureEqual("""
          |Project
          | module1
          | module2
          |
          """.trimMargin())
  }

  fun `test do not show groups duplicating module names`() {
    val root = directoryContent {
      dir("foo") {}
      dir("foo.bar") {}
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("xxx.foo"), root.findChild("foo")!!)
    PsiTestUtil.addContentRoot(createModule("xxx.foo.bar"), root.findChild("foo.bar")!!)
    assertStructureEqual("""
          |Project
          | Group: xxx
          |  foo
          |  foo.bar
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
    PsiTestUtil.addContentRoot(createModule("foo.bar.module1"), root.findChild("module1")!!)
    PsiTestUtil.addContentRoot(createModule("foo.baz.module2"), root.findChild("module2")!!)
    assertStructureEqual("""
      |Project
      | Group: foo
      |  Group: bar
      |   module1
      |    subdir
      |  Group: baz
      |   module2
      |    subdir
      |
      """.trimMargin())
  }

  fun `test modules in nested groups`() {
    val root = directoryContent {
      dir("module1") {
        dir("subdir") {}
      }
      dir("module2") {
        dir("subdir") {}
      }
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("foo.bar.module1"), root.findChild("module1")!!)
    PsiTestUtil.addContentRoot(createModule("foo.module2"), root.findChild("module2")!!)
    assertStructureEqual("""
      |Project
      | Group: foo
      |  Group: bar
      |   module1
      |    subdir
      |  module2
      |   subdir
      |
      """.trimMargin())
  }

}

class ModulesInPackageViewTest : ModulesInProjectViewTestCase() {
  
  init {
    myPrintInfo = Queryable.PrintInfo(arrayOf("id"), arrayOf("name"))
  }

  override fun setUp() {
    packageViewPaneId = PackageViewPane.ID
    super.setUp()
  }

  fun `test nested modules`() {
    val root = directoryContent {
      dir("module1") {
        dir("module11") {
          dir("main") {
            dir("src") {}
          }
          dir("test") {
            dir("src") {}
          }
        }
        dir("module12") {
          dir("main") {
            dir("src") {}
          }
          dir("test") {
            dir("src") {}
          }
        }
      }
      dir("module2") {
        dir("module22") {
          dir("main") {
            dir("src") {}
          }
          dir("test") {
            dir("src") {}
          }
        }
      }
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("module1"), root.findFileByRelativePath("module1")!!)
    PsiTestUtil.addContentRoot(createModule("module1.module11"), root.findFileByRelativePath("module1/module11")!!)
    val module11main = createModule("module1.module11.main")
    PsiTestUtil.addContentRoot(module11main, root.findFileByRelativePath("module1/module11/main")!!)
    PsiTestUtil.addSourceContentToRoots(module11main, root.findFileByRelativePath("module1/module11/main/src")!!)
    val module11test = createModule("module1.module11.test")
    PsiTestUtil.addContentRoot(module11test, root.findFileByRelativePath("module1/module11/test")!!)
    PsiTestUtil.addSourceContentToRoots(module11test, root.findFileByRelativePath("module1/module11/test/src")!!)
    PsiTestUtil.addContentRoot(createModule("module1.module12"), root.findFileByRelativePath("module1/module12")!!)
    val module12main = createModule("module1.module12.main")
    PsiTestUtil.addContentRoot(module12main, root.findFileByRelativePath("module1/module12/main")!!)
    PsiTestUtil.addSourceContentToRoots(module12main, root.findFileByRelativePath("module1/module12/main/src")!!)
    val module12test = createModule("module1.module12.test")
    PsiTestUtil.addContentRoot(module12test, root.findFileByRelativePath("module1/module12/test")!!)
    PsiTestUtil.addSourceContentToRoots(module12test, root.findFileByRelativePath("module1/module12/test/src")!!)
    PsiTestUtil.addContentRoot(createModule("module2"), root.findFileByRelativePath("module2")!!)
    PsiTestUtil.addContentRoot(createModule("module2.module22"), root.findFileByRelativePath("module2/module22")!!)
    val module22main = createModule("module2.module22.main")
    PsiTestUtil.addContentRoot(module22main, root.findFileByRelativePath("module2/module22/main")!!)
    PsiTestUtil.addSourceContentToRoots(module22main, root.findFileByRelativePath("module2/module22/main/src")!!)
    val module22test = createModule("module2.module22.test")
    PsiTestUtil.addContentRoot(module22test, root.findFileByRelativePath("module2/module22/test")!!)
    PsiTestUtil.addSourceContentToRoots(module22test, root.findFileByRelativePath("module2/module22/test/src")!!)
    assertStructureEqual("""
      |Project
      | Group: module1
      |  Module name=module1.module11
      |   Module name=module1.module11.main
      |   Module name=module1.module11.test
      |  Module name=module1.module12
      |   Module name=module1.module12.main
      |   Module name=module1.module12.test
      | Group: module2
      |  Module name=module2.module22
      |   Module name=module2.module22.main
      |   Module name=module2.module22.test
      |
      """.trimMargin())
  }

}

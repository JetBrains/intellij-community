// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.projectView

import com.intellij.codeInsight.HeavyJavaCodeInsightFixture.Companion.heavyJavaCodeInsightTestNamedSubFixture
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.ide.projectView.impl.PackageViewPane
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Queryable
import com.intellij.project.stateStore
import com.intellij.projectView.ProjectViewFixture
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.util.io.directoryContent
import com.intellij.util.io.generateInVirtualTempDir
import com.intellij.workspaceModel.ide.registerProjectRoot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * A [ProjectViewFixture] without test data. The module files go to the `.idea` directory, so that they do not affect
 * the expected structure of the project view. It replaces `ModulesInProjectViewTestCase`.
 */
private class ModulesInProjectViewFixture(context: Context, packageViewPaneId: String, printInfo: Queryable.PrintInfo)
  : ProjectViewFixture(context, null, false, packageViewPaneId, printInfo) {
  override fun setUp() {
    super.setUp()
    structure.isShowLibraryContents = false
  }

  override fun createMainModule(): Module = createModule(project.name)

  override fun createModule(moduleName: String): Module {
    return WriteAction.computeAndWait<Module, RuntimeException> {
      /* iml files are created under .idea directory to ensure that they won't affect expected structure of Project View;
         this is needed to ensure that tests work the same way under the old project model and under workspace model where all modules
         are saved when a single module is unloaded */
      val imlPath = project.stateStore.projectBasePath.resolve(".idea/$moduleName${ModuleFileType.DOT_DEFAULT_EXTENSION}")
      ModuleManager.getInstance(project).newModule(imlPath, JavaModuleType.getModuleType().id)
    }
  }
}

private fun modulesInProjectViewFixture(packageViewPaneId: String, printInfo: Queryable.PrintInfo): TestFixture<ProjectViewFixture> =
  heavyJavaCodeInsightTestNamedSubFixture("modulesInProjectViewFixture", directoryBasedProject = true) { context ->
    ModulesInProjectViewFixture(context, packageViewPaneId, printInfo)
  }

// directory-based project must be used to ensure that .iws/.ipr file won't break the test (they may be created if workspace model is used)
@TestApplication
class ModulesInProjectViewTest {
  private val fixture by modulesInProjectViewFixture(ProjectViewPane.ID, Queryable.PrintInfo())

  @Test
  fun `test unloaded modules`(): Unit = with(fixture) {
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

    timeoutRunBlocking {
      ModuleManager.getInstance(project).setUnloadedModules(listOf("unloaded", "unloaded-inner"))
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

  @Test
  fun `test unloaded module with qualified name`(): Unit = with(fixture) {
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

    timeoutRunBlocking {
      ModuleManager.getInstance(project).setUnloadedModules(listOf("unloaded"))
    }
    assertStructureEqual(expected)
  }

  @Test
  fun `test module and plain project root`(): Unit = with(fixture) {
    val root = directoryContent {
      dir("module") {
        dir("subdir") {}
      }
      dir("projectRoot") {
        dir("subdir") {}
      }
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("module"), root.findChild("module")!!)
    timeoutRunBlocking {
      registerProjectRoot(project, root.findChild("projectRoot")!!.toNioPath())
    }
    assertStructureEqual("""
          |Project
          | module
          |  subdir
          | projectRoot
          |  subdir
          |
          """.trimMargin())
  }

  @Test
  fun `test deleted roots are not shown`(): Unit = with(fixture) {
    val root = directoryContent {
      dir("module") {
        dir("subdir") {}
      }
      dir("deletedModule") {
        dir("subdir") {}
      }
      dir("deletedProjectRoot") {
        dir("subdir") {}
      }
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("module"), root.findChild("module")!!)
    PsiTestUtil.addContentRoot(createModule("deletedModule"), root.findChild("deletedModule")!!)
    timeoutRunBlocking {
      registerProjectRoot(project, root.findChild("deletedProjectRoot")!!.toNioPath())
    }
    assertStructureEqual("""
          |Project
          | deletedModule
          |  subdir
          | deletedProjectRoot
          |  subdir
          | module
          |  subdir
          |
          """.trimMargin())

    VfsTestUtil.deleteFile(root.findChild("deletedModule")!!)
    VfsTestUtil.deleteFile(root.findChild("deletedProjectRoot")!!)
    assertStructureEqual("""
          |Project
          | module
          |  subdir
          |
          """.trimMargin())
  }

  @Test
  fun `test do not show parent groups for single module`(): Unit = with(fixture) {
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

  @Test
  fun `test flatten modules option`(): Unit = with(fixture) {
    val root = directoryContent {
      dir("module1") {}
      dir("module2") {}
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("foo.bar.module1"), root.findChild("module1")!!)
    PsiTestUtil.addContentRoot(createModule("foo.bar.module2"), root.findChild("module2")!!)
    structure.isFlattenModules = true
    assertStructureEqual("""
          |Project
          | module1
          | module2
          |
          """.trimMargin())
  }

  @Test
  fun `test do not show groups duplicating module names`(): Unit = with(fixture) {
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

  @Test
  fun `test modules with common parent group`(): Unit = with(fixture) {
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

  @Test
  fun `test three-level modules`(): Unit = with(fixture) {
    val root = directoryContent {
      dir("module1") {
        dir("subdir") {}
      }
      dir("module1.module2") {
        dir("subdir") {}
      }
      dir("module1.module2.module3") {
        dir("subdir") {}
      }
    }.generateInVirtualTempDir()
    PsiTestUtil.addContentRoot(createModule("module1"), root.findChild("module1")!!)
    PsiTestUtil.addContentRoot(createModule("module1.module2"), root.findChild("module1.module2")!!)
    PsiTestUtil.addContentRoot(createModule("module1.module2.module3"), root.findChild("module1.module2.module3")!!)
    assertStructureEqual("""
      |Project
      | Group: module1
      |  module1.module2
      |   subdir
      |  module1.module2.module3
      |   subdir
      | module1
      |  subdir
      |
      """.trimMargin())
  }

  @Test
  fun `test modules in nested groups`(): Unit = with(fixture) {
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

  // BAZEL-3331: reproduces the "dummy module" case where a directory belongs to a different module than the files inside it.
  @Test
  fun `test directory with children from another module is shown in flatten packages mode`(): Unit = with(fixture) {
    val root = directoryContent {
      dir("moduleA") {
        file("File1.kt")
        file("File2.kt")
      }
    }.generateInVirtualTempDir()
    val moduleADir = root.findChild("moduleA")!!
    val file1 = moduleADir.findChild("File1.kt")!!
    val file2 = moduleADir.findChild("File2.kt")!!
    val moduleA = createModule("moduleA")
    PsiTestUtil.addContentRoot(moduleA, moduleADir)
    PsiTestUtil.addSourceRoot(moduleA, moduleADir)
    val moduleB = createModule("moduleB")
    PsiTestUtil.addSourceRoot(moduleB, file1)
    PsiTestUtil.addSourceRoot(moduleB, file2)
    val projectFileIndex = ProjectRootManager.getInstance(project).fileIndex
    assertEquals(moduleA, projectFileIndex.getModuleForFile(moduleADir))
    assertEquals(moduleB, projectFileIndex.getModuleForFile(file1))
    assertEquals(moduleB, projectFileIndex.getModuleForFile(file2))
    assertFalse(ModuleRootManager.getInstance(moduleA).fileIndex.isInContent(file1))
    structure.isFlattenPackages = true
    val psiDirectory = timeoutRunBlocking { readAction { PsiManager.getInstance(project).findDirectory(moduleADir)!! } }
    assertStructureEqual(psiDirectory, """
      moduleA
       File1.kt
       File2.kt
      """.trimIndent())
  }
}

@TestApplication
class ModulesInPackageViewTest {
  private val fixture by modulesInProjectViewFixture(PackageViewPane.ID, Queryable.PrintInfo(arrayOf("id"), arrayOf("name")))

  @Test
  fun `test nested modules`(): Unit = with(fixture) {
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
      |  Module name=module1.module11.main
      |  Module name=module1.module11.test
      |  Module name=module1.module12
      |  Module name=module1.module12.main
      |  Module name=module1.module12.test
      | Group: module2
      |  Module name=module2.module22
      |  Module name=module2.module22.main
      |  Module name=module2.module22.test
      |
      """.trimMargin())
  }

}

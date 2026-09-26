// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.scope.packageSet

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.workspaceModel.ide.registerProjectRoot
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
@Timeout(30)
internal class ProjectPathPatternPackageSetTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @JvmField
  @RegisterExtension
  val tempDir: TempDirectoryExtension = TempDirectoryExtension()

  @Test
  fun `pattern uses the base project directory instead of the content root`(): Unit = timeoutRunBlocking {
    val file = createNestedModuleFile()

    assertTrue(contains(compile("projectPath:language-server//*"), file))
    assertTrue(contains(compile("projectPath:/language-server//*"), file))
    assertFalse(contains(FilePatternPackageSet("", "language-server//*"), file))
  }

  @Test
  fun `file outside the base project directories does not match`(): Unit = timeoutRunBlocking {
    val file = tempDir.newVirtualFile("outside/src/Foo.kt")

    assertFalse(contains(compile("projectPath:**"), file))
  }

  @Test
  fun `pattern uses the base directory that contains the file`(): Unit = timeoutRunBlocking {
    val file = tempDir.newVirtualFile("attached/src/Foo.kt")
    registerProjectRoot(projectModel.project, file.parent.parent.toNioPath())

    assertTrue(contains(compile("projectPath:src//*"), file))
  }

  @Test
  fun `recursive pattern matches a directory`(): Unit = timeoutRunBlocking {
    val directory = createNestedModuleFile().parent

    assertTrue(contains(compile("projectPath:language-server//*"), directory))
  }

  private suspend fun createNestedModuleFile(): VirtualFile {
    registerProjectRoot(projectModel.project, projectModel.projectRootDir)
    val contentRoot = projectModel.baseProjectDir.newVirtualDirectory("language-server/analyzer")
    val module = projectModel.createModule("language-server.analyzer")
    PsiTestUtil.addContentRoot(module, contentRoot)
    return projectModel.baseProjectDir.newVirtualFile("language-server/analyzer/src/Foo.kt")
  }

  private fun compile(pattern: String): PackageSetBase {
    return assertInstanceOf(PackageSetBase::class.java, PackageSetFactory.getInstance().compile(pattern))
  }

  private suspend fun contains(packageSet: PackageSetBase, file: VirtualFile): Boolean {
    return readAction { packageSet.contains(file, projectModel.project, null) }
  }
}

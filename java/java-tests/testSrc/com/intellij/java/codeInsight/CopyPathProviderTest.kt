// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight

import com.intellij.ide.actions.CopyContentRootPathProvider
import com.intellij.ide.actions.CopySourceRootPathProvider
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class CopyPathProviderTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @JvmField
  @RegisterExtension
  val baseNonProjectDir: TempDirectoryExtension = TempDirectoryExtension()

  lateinit var module: Module
  lateinit var rootDir: VirtualFile

  @BeforeEach
  fun setUp() = runBlocking {
    edtWriteAction {
      rootDir = projectModel.baseProjectDir.newVirtualDirectory("root")
      module = projectModel.createModule()
    }
  }

  @Test
  fun `file under content root`() = runBlocking {
    val file = projectModel.baseProjectDir.newVirtualFile("root/dir/a.txt")
    ModuleRootModificationUtil.addContentRoot(module, rootDir)
    readAction {
      assertEquals("dir/a.txt", getPathFromContentRoot(file))
      assertNull(getPathFromSourceRoot(file))
    }
  }
  
  @Test
  fun `file not in project`() = runBlocking {
    val file = baseNonProjectDir.newVirtualFile("root/dir/a.txt")
    readAction {
      assertNull(getPathFromContentRoot(file))
      assertNull(getPathFromSourceRoot(file))
    }
  }
  
  @Test
  fun `file under source root`() = runBlocking {
    val srcRoot = projectModel.baseProjectDir.newVirtualDirectory("root/src") 
    val file = projectModel.baseProjectDir.newVirtualFile("root/src/pack/A.java")
    ModuleRootModificationUtil.addContentRoot(module, rootDir)
    PsiTestUtil.addSourceRoot(module, srcRoot)
    readAction {
      assertEquals("src/pack/A.java", getPathFromContentRoot(file))
      assertEquals("pack/A.java", getPathFromSourceRoot(file))
    }
  }

  private fun getPathFromSourceRoot(file: VirtualFile) = CopySourceRootPathProvider().getPathToElement(module.project, file, null)

  private fun getPathFromContentRoot(file: VirtualFile) = CopyContentRootPathProvider().getPathToElement(module.project, file, null)
}
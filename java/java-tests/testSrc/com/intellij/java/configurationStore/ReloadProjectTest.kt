// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.createOrLoadProject
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

/**
 * This class has no specific Java test. It's located in intellij.java.tests module because if Java plugin is enabled additional elements
 * are added to iml file (e.g. 'exclude-output' tag) so if this test is located in a platform module it'll give different results dependening
 * on whether there is Java plugin in runtime classpath or not.
 */
class ReloadProjectTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @JvmField
  @Rule
  val tempDirectory = TemporaryDirectory()

  @Test
  internal fun `reload module with module library`() {
    loadProject("removeModuleWithModuleLibrary/before") { project ->
      val base = Paths.get(project.basePath!!)
      FileUtil.copyDir(testDataRoot.resolve("removeModuleWithModuleLibrary/after").toFile(), base.toFile())
      VfsUtil.markDirtyAndRefresh(false, true, true, VfsUtil.findFile(base, true))
      ApplicationManager.getApplication().invokeAndWait {
        PlatformTestUtil.saveProject(project)
      }
    }
  }

  private val testDataRoot
    get() = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("java/java-tests/testData/reloading")

  private fun loadProject(testDataDirName: String, checkProject: (Project) -> Unit) {
    return loadProject(testDataRoot.resolve(testDataDirName), checkProject)
  }

  private fun loadProject(projectPath: Path, checkProject: (Project) -> Unit) {
    @Suppress("RedundantSuspendModifier")
    suspend fun copyProjectFiles(dir: VirtualFile): Path {
      val projectDir = VfsUtil.virtualToIoFile(dir)
      FileUtil.copyDir(projectPath.toFile(), projectDir)
      VfsUtil.markDirtyAndRefresh(false, true, true, dir)
      return projectDir.toPath()
    }
    runBlocking {
      createOrLoadProject(tempDirectory, ::copyProjectFiles, loadComponentState = true, useDefaultProjectSettings = false) {
        checkProject(it)
      }
    }
  }
}

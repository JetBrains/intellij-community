// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.configurationStore

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.loadProjectAndCheckResults
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Paths

class ReloadProjectTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @JvmField
  @Rule
  val tempDirectory = TemporaryDirectory()

  private val testDataRoot
    get() = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("java/java-tests/testData/reloading")

  @Test
  fun `reload module with module library`() {
    loadProjectAndCheckResults("removeModuleWithModuleLibrary/before") { project ->
      val base = Paths.get(project.basePath!!)
      FileUtil.copyDir(testDataRoot.resolve("removeModuleWithModuleLibrary/after").toFile(), base.toFile())
      VfsUtil.markDirtyAndRefresh(false, true, true, VfsUtil.findFile(base, true))
      ApplicationManager.getApplication().invokeAndWait {
        PlatformTestUtil.saveProject(project)
      }
    }
  }

  @Test
  fun `change iml`() {
    loadProjectAndCheckResults("changeIml/initial") { project ->
      copyFilesAndReload(project, "changeIml/update")
      val module = ModuleManager.getInstance(project).modules.single()
      val srcUrl = VfsUtilCore.pathToUrl("${project.basePath}/src")
      assertThat(ModuleRootManager.getInstance(module).sourceRootUrls).containsExactly(srcUrl)

      copyFilesAndReload(project, "changeIml/update2")
      val srcUrl2 = VfsUtilCore.pathToUrl("${project.basePath}/src2")
      assertThat(ModuleRootManager.getInstance(module).sourceRootUrls).containsExactlyInAnyOrder(srcUrl, srcUrl2)
    }
  }

  private suspend fun copyFilesAndReload(project: Project, relativePath: String) {
    val base = Paths.get(project.basePath!!)
    FileUtil.copyDir(testDataRoot.resolve(relativePath).toFile(), base.toFile())
    VfsUtil.markDirtyAndRefresh(false, true, true, VfsUtil.findFile(base, true))
    StoreReloadManager.getInstance().reloadChangedStorageFiles()
  }

  private fun loadProjectAndCheckResults(testDataDirName: String, checkProject: suspend (Project) -> Unit) {
    return loadProjectAndCheckResults(listOf(testDataRoot.resolve(testDataDirName)), tempDirectory, checkProject)
  }
}

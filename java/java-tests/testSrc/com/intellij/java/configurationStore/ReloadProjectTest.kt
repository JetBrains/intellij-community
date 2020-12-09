// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.configurationStore

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.module.ConfigurationErrorDescription
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.packaging.impl.elements.FileCopyPackagingElement
import com.intellij.testFramework.*
import com.intellij.util.CommonProcessors
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

  @JvmField
  @Rule
  val disposable = DisposableRule()

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

  @Test
  fun `add module from subdirectory`() {
    loadProjectAndCheckResults("addModuleFromSubDir/initial") { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      assertThat(module.name).isEqualTo("foo")
      copyFilesAndReload(project, "addModuleFromSubDir/update")
      assertThat(ModuleManager.getInstance(project).modules).hasSize(2)
    }
  }

  @Test
  fun `change artifact`() {
    loadProjectAndCheckResults("changeArtifact/initial") { project ->
      val artifact = ArtifactManager.getInstance(project).artifacts.single()
      assertThat(artifact.name).isEqualTo("a")
      assertThat((artifact.rootElement.children.single() as FileCopyPackagingElement).filePath).endsWith("/a.txt")
      copyFilesAndReload(project, "changeArtifact/update")
      val artifact2 = ArtifactManager.getInstance(project).artifacts.single()
      assertThat(artifact2.name).isEqualTo("a")
      assertThat((artifact2.rootElement.children.single() as FileCopyPackagingElement).filePath).endsWith("/bbb.txt")
    }
  }

  @Test
  fun `change iml file content to invalid xml`() {
    val errors = ArrayList<ConfigurationErrorDescription>()
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(errors::add, disposable.disposable)
    loadProjectAndCheckResults("changeImlContentToInvalidXml/initial") { project ->
      copyFilesAndReload(project, "changeImlContentToInvalidXml/update")
      assertThat(ModuleManager.getInstance(project).modules.single().name).isEqualTo("foo")
      assertThat(errors.single().description).contains("foo.iml")
    }

  }

  private suspend fun copyFilesAndReload(project: Project, relativePath: String) {
    val base = Paths.get(project.basePath!!)
    val projectDir = VfsUtil.findFile(base, true)!!
    //process all files to ensure that they all are loaded in VFS and we'll get events when they are changed
    VfsUtil.processFilesRecursively(projectDir, CommonProcessors.alwaysTrue())

    FileUtil.copyDir(testDataRoot.resolve(relativePath).toFile(), base.toFile())
    VfsUtil.markDirtyAndRefresh(false, true, true, projectDir)
    StoreReloadManager.getInstance().reloadChangedStorageFiles()
  }

  private fun loadProjectAndCheckResults(testDataDirName: String, checkProject: suspend (Project) -> Unit) {
    return loadProjectAndCheckResults(listOf(testDataRoot.resolve(testDataDirName)), tempDirectory, checkProject)
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.configurationStore

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.createOrLoadProject
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class LoadProjectTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @JvmField
  @Rule
  val tempDirectory = TemporaryDirectory()

  @Test
  fun `load single module`() {
    loadProjectAndCheckResults("single-module") { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      assertThat(module.name).isEqualTo("foo")
      assertThat(module.moduleTypeName).isEqualTo("EMPTY_MODULE")
    }
  }

  @Test
  fun `load detached module`() {
    loadProjectAndCheckResults("detached-module") { project ->
      val fooModule = ModuleManager.getInstance(project).modules.single()
      assertThat(fooModule.name).isEqualTo("foo")
      val barModule = runWriteActionAndWait { ModuleManager.getInstance(project).loadModule("${project.basePath}/bar/bar.iml") }
      assertThat(barModule.name).isEqualTo("bar")
      assertThat(barModule.moduleTypeName).isEqualTo("EMPTY_MODULE")
      assertThat(ModuleManager.getInstance(project).modules).containsExactlyInAnyOrder(fooModule, barModule)
    }
  }

  @Test
  fun `load detached module via modifiable model`() {
    loadProjectAndCheckResults("detached-module") { project ->
      val fooModule = ModuleManager.getInstance(project).modules.single()
      assertThat(fooModule.name).isEqualTo("foo")
      runWriteActionAndWait {
        val model = ModuleManager.getInstance(project).modifiableModel
        model.loadModule("${project.basePath}/bar/bar.iml")
        model.commit()
      }
      val barModule = ModuleManager.getInstance(project).findModuleByName("bar")
      assertThat(barModule).isNotNull()
      assertThat(barModule!!.moduleTypeName).isEqualTo("EMPTY_MODULE")
      assertThat(ModuleManager.getInstance(project).modules).containsExactlyInAnyOrder(fooModule, barModule)
    }
  }

  @Test
  fun `load single library`() {
    loadProjectAndCheckResults("single-library") { project ->
      assertThat(ModuleManager.getInstance(project).modules).isEmpty()
      val library = LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.single()
      assertThat(library.name).isEqualTo("foo")
      val rootUrl = library.getUrls(OrderRootType.CLASSES).single()
      assertThat(rootUrl).isEqualTo(VfsUtilCore.pathToUrl("${project.basePath}/lib/classes"))
    }
  }

  private val Library.properties: RepositoryLibraryProperties
    get() = (this as LibraryEx).properties as RepositoryLibraryProperties

  @Test
  fun `load repository libraries`() {
    val projectPath = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("jps/model-serialization/testData/repositoryLibraries")
    loadProjectAndCheckResults(projectPath) { project ->
      assertThat(ModuleManager.getInstance(project).modules).isEmpty()
      val libraries = LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.sortedBy { it.name }
      assertThat(libraries).hasSize(3)
      val (plain, withExcluded, withoutTransitive) = libraries

      assertThat(plain.name).isEqualTo("plain")
      assertThat(plain.properties.groupId).isEqualTo("junit")
      assertThat(plain.properties.artifactId).isEqualTo("junit")
      assertThat(plain.properties.version).isEqualTo("3.8.1")
      assertThat(plain.properties.isIncludeTransitiveDependencies).isTrue()
      assertThat(plain.properties.excludedDependencies).isEmpty()

      assertThat(withExcluded.name).isEqualTo("with-excluded-dependencies")
      assertThat(withExcluded.properties.isIncludeTransitiveDependencies).isTrue()
      assertThat(withExcluded.properties.excludedDependencies).containsExactly("org.apache.httpcomponents:httpclient")

      assertThat(withoutTransitive.name).isEqualTo("without-transitive-dependencies")
      assertThat(withoutTransitive.properties.isIncludeTransitiveDependencies).isFalse()
      assertThat(withoutTransitive.properties.excludedDependencies).isEmpty()
    }
  }

  private val testDataRoot
    get() = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("java/java-tests/testData/configurationStore")

  private fun loadProjectAndCheckResults(testDataDirName: String, checkProject: (Project) -> Unit) {
    return loadProjectAndCheckResults(testDataRoot.resolve(testDataDirName), checkProject)
  }

  private fun loadProjectAndCheckResults(projectPath: Path, checkProject: (Project) -> Unit) {
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
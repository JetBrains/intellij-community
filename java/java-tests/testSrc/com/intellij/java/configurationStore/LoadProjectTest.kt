// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.configurationStore

import com.intellij.ProjectTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectLifecycleListener
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.loadProjectAndCheckResults
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
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

  @JvmField
  @Rule
  val disposable = DisposableRule()

  @Test
  fun `load single module`() {
    loadProjectAndCheckResults("single-module") { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      assertThat(module.name).isEqualTo("foo")
      assertThat(module.moduleTypeName).isEqualTo("EMPTY_MODULE")
    }
  }

  @Test
  fun `load module with group`() {
    loadProjectAndCheckResults("module-in-group") { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      assertThat(module.name).isEqualTo("foo")
      assertThat(module.moduleTypeName).isEqualTo("EMPTY_MODULE")
      assertThat(ModuleManager.getInstance(project).getModuleGroupPath(module)).containsExactly("group")
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

  @Test
  fun `load module and library`() {
    ApplicationManager.getApplication().messageBus.connect(disposable.disposable).subscribe(ProjectLifecycleListener.TOPIC, object : ProjectLifecycleListener {
      override fun projectComponentsInitialized(project: Project) {
        //this emulates listener declared in plugin.xml, it's registered before the project is loaded
        project.messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
          override fun rootsChanged(event: ModuleRootEvent) {
            val library = LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.single()
            assertThat(library.name).isEqualTo("foo")
          }
        })
      }
    })
    loadProjectAndCheckResults("module-and-library") { project ->
      val fooModule = ModuleManager.getInstance(project).modules.single()
      assertThat(fooModule.name).isEqualTo("foo")
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
    loadProjectAndCheckResults(listOf(projectPath), tempDirectory) { project ->
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

  private fun loadProjectAndCheckResults(testDataDirName: String, checkProject: suspend (Project) -> Unit) {
    val testDataRoot = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("java/java-tests/testData/configurationStore")
    return loadProjectAndCheckResults(listOf(testDataRoot.resolve(testDataDirName)), tempDirectory, checkProject)
  }
}

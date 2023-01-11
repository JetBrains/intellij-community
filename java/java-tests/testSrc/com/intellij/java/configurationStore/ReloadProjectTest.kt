// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.configurationStore

import com.intellij.facet.FacetManager
import com.intellij.facet.mock.MockFacetType
import com.intellij.facet.mock.registerFacetType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runReadAction
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
import com.intellij.testFramework.configurationStore.copyFilesAndReloadProject
import com.intellij.util.io.systemIndependentPath
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.jps.serialization.CustomModuleRootsSerializer
import com.intellij.workspaceModel.storage.DummyParentEntitySource
import com.intellij.workspaceModel.storage.bridgeEntities.ExternalSystemModuleOptionsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleCustomImlDataEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume.assumeTrue
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

  @JvmField
  @Rule
  val logging = TestLoggerFactory.createTestWatcher()

  private val testDataRoot
    get() = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("java/java-tests/testData/reloading")

  @Test
  fun `reload module with module library`() = runBlocking {
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
  fun `change iml`() = runBlocking {
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
  fun `add module from subdirectory`() = runBlocking {
    loadProjectAndCheckResults("addModuleFromSubDir/initial") { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      assertThat(module.name).isEqualTo("foo")
      copyFilesAndReload(project, "addModuleFromSubDir/update")
      assertThat(ModuleManager.getInstance(project).modules).hasSize(2)
    }
  }

  @Test
  fun `change artifact`() = runBlocking {
    loadProjectAndCheckResults("changeArtifact/initial") { project ->
      val artifact = runReadAction {
        ArtifactManager.getInstance(project).artifacts.single()
      }
      assertThat(artifact.name).isEqualTo("a")
      assertThat((artifact.rootElement.children.single() as FileCopyPackagingElement).filePath).endsWith("/a.txt")
      copyFilesAndReload(project, "changeArtifact/update")
      val artifact2 = runReadAction {
        ArtifactManager.getInstance(project).artifacts.single()
      }
      assertThat(artifact2.name).isEqualTo("a")
      assertThat((artifact2.rootElement.children.single() as FileCopyPackagingElement).filePath).endsWith("/bbb.txt")
    }
  }

  @Test
  fun `change iml file content to invalid xml`() = runBlocking {
    val errors = ArrayList<ConfigurationErrorDescription>()
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(disposable.disposable, errors::add)
    loadProjectAndCheckResults("changeImlContentToInvalidXml/initial") { project ->
      copyFilesAndReload(project, "changeImlContentToInvalidXml/update")
      assertThat(ModuleManager.getInstance(project).modules.single().name).isEqualTo("foo")
      assertThat(errors.single().description).contains("foo.iml")
    }

  }

  @Test
  fun `reload facet in module with custom storage`() = runBlocking {
    CustomModuleRootsSerializer.EP_NAME.point.registerExtension(SampleCustomModuleRootsSerializer(), disposable.disposable)
    registerFacetType(MockFacetType(), disposable.disposable)
    loadProjectAndCheckResults("facet-in-module-with-custom-storage/initial") { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      assertThat(module.name).isEqualTo("foo")
      val initialFacet = FacetManager.getInstance(module).getFacetByType(MockFacetType.ID)!!
      assertThat(initialFacet.configuration.data).isEqualTo("my-data")
      copyFilesAndReload(project, "facet-in-module-with-custom-storage/update")
      val changedFacet = FacetManager.getInstance(module).getFacetByType(MockFacetType.ID)!!
      assertThat(changedFacet.configuration.data).isEqualTo("changed-data")

      val entityStorage = WorkspaceModel.getInstance(project).entityStorage.current
      assumeTrue(entityStorage.entities(ModuleEntity::class.java).single().entitySource is DummyParentEntitySource)
      assumeTrue(entityStorage.entities(ModuleCustomImlDataEntity::class.java).single().entitySource is JpsImportedEntitySource)
      val moduleOptionsEntity = entityStorage.entities(ExternalSystemModuleOptionsEntity::class.java).single()
      assertThat(moduleOptionsEntity.externalSystem).isEqualTo("GRADLE")
      assertThat(moduleOptionsEntity.externalSystemModuleVersion).isEqualTo("42.0")
     }
  }

  @Test
  fun `chained module rename`() = runBlocking {
    loadProjectAndCheckResults("chained-module-rename/initial") { project ->
      assertThat(ModuleManager.getInstance(project).modules).hasSize(2)
      copyFilesAndReload(project, "chained-module-rename/update")
      val modules = ModuleManager.getInstance(project).modules.sortedBy { it.name }
      assertThat(modules).hasSize(2)
      val (bar, bar2) = modules
      assertThat(bar.name).isEqualTo("bar")
      assertThat(bar2.name).isEqualTo("bar2")
      assertThat(bar.moduleNioFile.systemIndependentPath).isEqualTo("${project.basePath}/foo/bar.iml")
      assertThat(bar2.moduleNioFile.systemIndependentPath).isEqualTo("${project.basePath}/bar/bar2.iml")
    }
  }

  private suspend fun copyFilesAndReload(project: Project, relativePath: String) {
    copyFilesAndReloadProject(project = project, fromDir = testDataRoot.resolve(relativePath))
  }

  private suspend fun loadProjectAndCheckResults(testDataDirName: String, checkProject: suspend (Project) -> Unit) {
    return loadProjectAndCheckResults(listOf(element = testDataRoot.resolve(testDataDirName)),
                                      tempDirectory = tempDirectory,
                                      checkProject = checkProject)
  }
}

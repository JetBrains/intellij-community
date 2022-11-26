// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.configurationStore

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerBase
import com.intellij.facet.FacetType
import com.intellij.facet.impl.FacetUtil
import com.intellij.facet.mock.MockFacet
import com.intellij.facet.mock.MockFacetConfiguration
import com.intellij.facet.mock.MockFacetType
import com.intellij.facet.mock.MockSubFacetType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ExternalSystemModulePropertyManagerBridge
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.doNotEnableExternalStorageByDefaultInTests
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.packaging.elements.ArtifactRootElement
import com.intellij.packaging.elements.PackagingElementFactory
import com.intellij.packaging.impl.artifacts.PlainArtifactType
import com.intellij.packaging.impl.elements.ArchivePackagingElement
import com.intellij.pom.java.LanguageLevel
import com.intellij.project.stateStore
import com.intellij.testFramework.*
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.util.io.*
import com.intellij.util.ui.UIUtil
import com.intellij.workspaceModel.ide.WorkspaceModel.Companion.getInstance
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer
import com.intellij.workspaceModel.storage.MutableEntityStorage.Companion.from
import com.intellij.workspaceModel.storage.bridgeEntities.ExternalSystemModuleOptionsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class ExternalSystemStorageTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()

  }
  @JvmField
  @Rule
  val disposableRule = DisposableRule()

  @JvmField
  @Rule
  val tempDirManager = TemporaryDirectory()

  @Test
  fun `save single mavenized module`() = saveProjectInExternalStorageAndCheckResult("singleModule") { project, projectDir ->
    val module = ModuleManager.getInstance(project).newModule(projectDir.resolve("test.iml").systemIndependentPath, ModuleTypeId.JAVA_MODULE)
    ModuleRootModificationUtil.addContentRoot(module, projectDir.systemIndependentPath)
    ExternalSystemModulePropertyManager.getInstance(module).setMavenized(true)
  }

  @Test
  fun `load single mavenized module`() = loadProjectAndCheckResults("singleModule") { project ->
    val module = ModuleManager.getInstance(project).modules.single()
    assertThat(module.name).isEqualTo("test")
    assertThat(module.moduleTypeName).isEqualTo(ModuleTypeId.JAVA_MODULE)
    assertThat(module.moduleFilePath).isEqualTo("${project.basePath}/test.iml")
    assertThat(ExternalSystemModulePropertyManager.getInstance(module).isMavenized()).isTrue()
    assertThat(ExternalStorageConfigurationManager.getInstance(project).isEnabled).isTrue()
  }

  @Test
  fun `save single module from external system`() = saveProjectInExternalStorageAndCheckResult("singleModuleFromExternalSystem") { project, projectDir ->
    val module = ModuleManager.getInstance(project).newModule(projectDir.resolve("test.iml").systemIndependentPath,
                                                              ModuleTypeId.JAVA_MODULE)
    ModuleRootModificationUtil.addContentRoot(module, projectDir.systemIndependentPath)
    setExternalSystemOptions(module, projectDir)
  }

  @Test
  fun `applying external system options twice`() {
    createProjectAndUseInLoadComponentStateMode(tempDirManager, directoryBased = true, useDefaultProjectSettings = false) { project ->
      runBlocking {
        withContext(Dispatchers.EDT) {
          runWriteAction {
            val projectDir = project.stateStore.directoryStorePath!!.parent
            val module = ModuleManager.getInstance(project).newModule(projectDir.resolve("test.iml").systemIndependentPath,
                                                                      ModuleTypeId.JAVA_MODULE)
            ModuleRootModificationUtil.addContentRoot(module, projectDir.systemIndependentPath)


            val propertyManager = ExternalSystemModulePropertyManager.getInstance(module)

            val systemId = ProjectSystemId("GRADLE")
            val moduleData = ModuleData("test", systemId, "", "", "", projectDir.systemIndependentPath).also {
              it.group = "group"
              it.version = "42.0"
            }
            val projectData = ProjectData(systemId, "", "", projectDir.systemIndependentPath)


            val modelsProvider = IdeModifiableModelsProviderImpl(project)

            propertyManager.setExternalOptions(systemId, moduleData, projectData)
            propertyManager.setExternalOptions(systemId, moduleData, projectData)

            val externalOptionsFromBuilder = modelsProvider.actualStorageBuilder
              .entities(ModuleEntity::class.java).singleOrNull()?.exModuleOptions
            assertEquals("GRADLE", externalOptionsFromBuilder?.externalSystem)
          }
        }
      }
    }
  }

  @Test
  fun `load single module from external system`() = loadProjectAndCheckResults("singleModuleFromExternalSystem") { project ->
    val module = ModuleManager.getInstance(project).modules.single()
    assertThat(module.name).isEqualTo("test")
    assertThat(module.moduleTypeName).isEqualTo(ModuleTypeId.JAVA_MODULE)
    assertThat(module.moduleFilePath).isEqualTo("${project.basePath}/test.iml")
    assertThat(ExternalSystemModulePropertyManager.getInstance(module).isMavenized()).isFalse()
    assertThat(ExternalStorageConfigurationManager.getInstance(project).isEnabled).isTrue()
    checkExternalSystemOptions(module, project.basePath!!)
  }

  @Test
  fun `save single module from external system in internal storage`() = saveProjectInInternalStorageAndCheckResult("singleModuleFromExternalSystemInInternalStorage") { project, projectDir ->
    val module = ModuleManager.getInstance(project).newModule(projectDir.resolve("test.iml").systemIndependentPath,
                                                              ModuleTypeId.JAVA_MODULE)
    ModuleRootModificationUtil.addContentRoot(module, projectDir.systemIndependentPath)
    setExternalSystemOptions(module, projectDir)
  }

  @Test
  fun `load single module from external system in internal storage`() = loadProjectAndCheckResults("singleModuleFromExternalSystemInInternalStorage") { project ->
    val module = ModuleManager.getInstance(project).modules.single()
    assertThat(module.name).isEqualTo("test")
    assertThat(module.moduleTypeName).isEqualTo(ModuleTypeId.JAVA_MODULE)
    assertThat(module.moduleFilePath).isEqualTo("${project.basePath}/test.iml")
    assertThat(ExternalSystemModulePropertyManager.getInstance(module).isMavenized()).isFalse()
    assertThat(ExternalStorageConfigurationManager.getInstance(project).isEnabled).isFalse()
    checkExternalSystemOptions(module, project.basePath!!)
  }

  private fun setExternalSystemOptions(module: Module, projectDir: Path) {
    val propertyManager = ExternalSystemModulePropertyManager.getInstance(module)
    val systemId = ProjectSystemId("GRADLE")
    val moduleData = ModuleData("test", systemId, "", "", "", projectDir.systemIndependentPath).also {
      it.group = "group"
      it.version = "42.0"
    }
    val projectData = ProjectData(systemId, "", "", projectDir.systemIndependentPath)
    propertyManager.setExternalOptions(systemId, moduleData, projectData)
  }

  private fun checkExternalSystemOptions(module: Module, projectDirPath: String) {
    val propertyManager = ExternalSystemModulePropertyManager.getInstance(module)
    assertThat(propertyManager.getExternalSystemId()).isEqualTo("GRADLE")
    assertThat(propertyManager.getExternalModuleGroup()).isEqualTo("group")
    assertThat(propertyManager.getExternalModuleVersion()).isEqualTo("42.0")
    assertThat(propertyManager.getLinkedProjectId()).isEqualTo("test")
    assertThat(propertyManager.getLinkedProjectPath()).isEqualTo(projectDirPath)
    assertThat(propertyManager.getRootProjectPath()).isEqualTo(projectDirPath)
  }


  @Test
  fun `save imported module in internal storage`() = saveProjectInInternalStorageAndCheckResult("singleModuleInInternalStorage") { project, projectDir ->
    val module = ModuleManager.getInstance(project).newModule(projectDir.resolve("test.iml").systemIndependentPath,
                                                              ModuleTypeId.JAVA_MODULE)
    ModuleRootModificationUtil.addContentRoot(module, projectDir.systemIndependentPath)
    ExternalSystemModulePropertyManager.getInstance(module).setMavenized(true)
  }

  @Test
  fun `load imported module from internal storage`() = loadProjectAndCheckResults("singleModuleInInternalStorage") { project ->
    val module = ModuleManager.getInstance(project).modules.single()
    assertThat(module.name).isEqualTo("test")
    assertThat(module.moduleTypeName).isEqualTo(ModuleTypeId.JAVA_MODULE)
    assertThat(module.moduleFilePath).isEqualTo("${project.basePath}/test.iml")
    assertThat(ExternalSystemModulePropertyManager.getInstance(module).isMavenized()).isTrue()
    assertThat(ExternalStorageConfigurationManager.getInstance(project).isEnabled).isFalse()
  }

  @Test
  fun `save mixed modules`() = saveProjectInExternalStorageAndCheckResult("mixedModules") { project, projectDir ->
    val regular = ModuleManager.getInstance(project).newModule(projectDir.resolve("regular.iml").systemIndependentPath, ModuleTypeId.JAVA_MODULE)
    ModuleRootModificationUtil.addContentRoot(regular, projectDir.resolve("regular").systemIndependentPath)
    val imported = ModuleManager.getInstance(project).newModule(projectDir.resolve("imported.iml").systemIndependentPath, ModuleTypeId.JAVA_MODULE)
    ModuleRootModificationUtil.addContentRoot(imported, projectDir.resolve("imported").systemIndependentPath)
    ExternalSystemModulePropertyManager.getInstance(imported).setMavenized(true)
    ExternalSystemModulePropertyManager.getInstance(imported).setLinkedProjectPath("${project.basePath}/imported")
  }

  @Test
  fun `check mavenized will be applied to the single diff`() {
    loadProjectAndCheckResults("twoRegularModules") { project ->
      val moduleManager = ModuleManager.getInstance(project)
      val initialStorage = getInstance(project).entityStorage.current
      val storageBuilder = from(initialStorage)
      for (module in moduleManager.modules) {
        val modulePropertyManager = ExternalSystemModulePropertyManager.getInstance(module)
        modulePropertyManager as ExternalSystemModulePropertyManagerBridge
        modulePropertyManager.setMavenized(true, storageBuilder)
      }
      val externalSystemModuleOptionsEntity = initialStorage.entities(ExternalSystemModuleOptionsEntity::class.java).singleOrNull()
      assertNull(externalSystemModuleOptionsEntity)
      assertEquals(2, storageBuilder.entities(ExternalSystemModuleOptionsEntity::class.java).count())
    }
  }

  @Test
  fun `load mixed modules`() = loadProjectAndCheckResults("mixedModules") { project ->
    val modules = ModuleManager.getInstance(project).modules.sortedBy { it.name }
    assertThat(modules).hasSize(2)
    val (imported, regular) = modules
    assertThat(imported.name).isEqualTo("imported")
    assertThat(regular.name).isEqualTo("regular")
    assertThat(imported.moduleTypeName).isEqualTo(ModuleTypeId.JAVA_MODULE)
    assertThat(regular.moduleTypeName).isEqualTo(ModuleTypeId.JAVA_MODULE)
    assertThat(imported.moduleFilePath).isEqualTo("${project.basePath}/imported.iml")
    assertThat(regular.moduleFilePath).isEqualTo("${project.basePath}/regular.iml")
    assertThat(ModuleRootManager.getInstance(imported).contentRootUrls.single()).isEqualTo(VfsUtil.pathToUrl("${project.basePath}/imported"))
    assertThat(ModuleRootManager.getInstance(regular).contentRootUrls.single()).isEqualTo(VfsUtil.pathToUrl("${project.basePath}/regular"))
    val externalModuleProperty = ExternalSystemModulePropertyManager.getInstance(imported)
    assertThat(externalModuleProperty.isMavenized()).isTrue()
    assertThat(externalModuleProperty.getLinkedProjectPath()).isEqualTo("${project.basePath}/imported")
    assertThat(ExternalSystemModulePropertyManager.getInstance(regular).isMavenized()).isFalse()
  }

  @Test
  fun `save regular facet in imported module`() = saveProjectInExternalStorageAndCheckResult("regularFacetInImportedModule") { project, projectDir ->
    val module = ModuleManager.getInstance(project).newModule(projectDir.resolve("test.iml").systemIndependentPath, ModuleTypeId.JAVA_MODULE)
    ModuleRootModificationUtil.addContentRoot(module, projectDir.systemIndependentPath)
    FacetManager.getInstance(module).addFacet(MockFacetType.getInstance(), "regular", null)
    ExternalSystemModulePropertyManager.getInstance(module).setMavenized(true)
  }

  @Test
  fun `load regular facet in imported module`() = loadProjectAndCheckResults("regularFacetInImportedModule") { project ->
    val module = ModuleManager.getInstance(project).modules.single()
    assertThat(module.name).isEqualTo("test")
    assertThat(ExternalSystemModulePropertyManager.getInstance(module).isMavenized()).isTrue()
    val facet = FacetManager.getInstance(module).allFacets.single()
    assertThat(facet.name).isEqualTo("regular")
    //suppressed until https://youtrack.jetbrains.com/issue/IDEA-294031 being fixed
    @Suppress("AssertBetweenInconvertibleTypes")
    assertThat(facet.type).isEqualTo(MockFacetType.getInstance())
    assertThat(facet.externalSource).isNull()
  }

  @Test
  fun `do not load modules from external system dir if external storage is disabled`() =
    loadProjectAndCheckResults("externalStorageIsDisabled") { project ->
      assertThat(ModuleManager.getInstance(project).modules).isEmpty()
    }

  @Test
  fun `load modules from internal storage if external is disabled but file exist`() =
    loadProjectAndCheckResults("singleModuleInInternalAndExternalStorages") { project ->
      val modules = ModuleManager.getInstance(project).modules
      assertThat(modules).hasSize(1)
      val testModule= modules[0]
      assertThat(testModule.name).isEqualTo("test")
      assertThat(testModule.moduleTypeName).isEqualTo(ModuleTypeId.JAVA_MODULE)
      assertThat(testModule.moduleFilePath).isEqualTo("${project.basePath}/test.iml")
      assertThat(ModuleRootManager.getInstance(testModule).contentRootUrls.single()).isEqualTo(VfsUtil.pathToUrl("${project.basePath}/test"))
      val externalModuleProperty = ExternalSystemModulePropertyManager.getInstance(testModule)
      assertThat(externalModuleProperty.isMavenized()).isTrue()
    }

  @Test
  fun `save imported facet in imported module`() = saveProjectInExternalStorageAndCheckResult("importedFacetInImportedModule") { project, projectDir ->
    val imported = ModuleManager.getInstance(project).newModule(projectDir.resolve("imported.iml").systemIndependentPath, ModuleTypeId.JAVA_MODULE)
    val facetRoot = VfsUtilCore.pathToUrl(projectDir.resolve("facet").systemIndependentPath)
    addFacet(imported, ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID, "imported", listOf(facetRoot))
    ExternalSystemModulePropertyManager.getInstance(imported).setMavenized(true)
  }

  private fun addFacet(module: Module, externalSystemId: String?, facetName: String, rootUrls: List<String> = emptyList()) {
    val facetManager = FacetManager.getInstance(module)
    val model = facetManager.createModifiableModel()
    val source = externalSystemId?.let { ExternalProjectSystemRegistry.getInstance().getSourceById(it) }
    val facet = facetManager.createFacet(MockFacetType.getInstance(), facetName, null)
    for (root in rootUrls) {
      facet.configuration.addRoot(root)
    }
    model.addFacet(facet, source)
    runWriteActionAndWait { model.commit() }
  }

  @Test
  fun `edit imported facet in internal storage with regular facet`() {
    loadModifySaveAndCheck("singleModuleFromExternalSystemInInternalStorage", "mixedFacetsInInternalStorage") { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      addFacet(module, null, "regular")
      runBlocking { project.stateStore.save() }
      addFacet(module, "GRADLE", "imported")
    }
  }

  @Test
  fun `edit regular facet in internal storage with imported facet`() {
    loadModifySaveAndCheck("singleModuleFromExternalSystemInInternalStorage", "mixedFacetsInInternalStorage") { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      addFacet(module, "GRADLE", "imported")
      runBlocking { project.stateStore.save() }
      addFacet(module, null, "regular")
    }
  }

  @Test
  fun `remove regular facet from imported module in external storage`() {
    loadModifySaveAndCheck("regularFacetInImportedModule", "singleModuleAfterMavenization") { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      FacetUtil.deleteFacet(FacetManager.getInstance(module).allFacets.single())
    }
  }

  @Test
  fun `edit imported facet in external storage with regular facet`() {
    loadModifySaveAndCheck("singleModuleFromExternalSystem", "mixedFacetsInExternalStorage") { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      addFacet(module, null, "regular")
      runBlocking { project.stateStore.save() }
      addFacet(module, "GRADLE", "imported")
    }
  }

  @Test
  fun `edit regular facet in external storage with imported facet`() {
    loadModifySaveAndCheck("singleModuleFromExternalSystem", "mixedFacetsInExternalStorage") { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      addFacet(module, "GRADLE", "imported")
      runBlocking { project.stateStore.save() }
      addFacet(module, null, "regular")
    }
  }

  @Test
  fun `load imported facet in imported module`() = loadProjectAndCheckResults("importedFacetInImportedModule") { project ->
    val module = ModuleManager.getInstance(project).modules.single()
    assertThat(ExternalSystemModulePropertyManager.getInstance(module).isMavenized()).isTrue()
    val facet = FacetManager.getInstance(module).allFacets.single() as MockFacet
    assertThat(facet.name).isEqualTo("imported")
    assertThat(facet.externalSource!!.id).isEqualTo(ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID)
    val facetRoot = VfsUtil.pathToUrl(project.basePath!!) + "/facet"
    assertThat(facet.configuration.rootUrls).containsExactly(facetRoot)
  }

  @Test
  fun `save libraries`() = saveProjectInExternalStorageAndCheckResult("libraries") { project, _ ->
    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    val model = libraryTable.modifiableModel
    model.createLibrary("regular", null)
    model.createLibrary("imported", null, externalSource)
    model.commit()
  }

  @Test
  fun `save libraries in internal storage`() = saveProjectInInternalStorageAndCheckResult("librariesInInternalStorage") { project, _ ->
    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    val model = libraryTable.modifiableModel
    model.createLibrary("regular", null)
    model.createLibrary("imported", null, externalSource)
    model.commit()
  }

  @Test
  fun `load unloaded modules`() {
    loadProjectAndCheckResults("unloadedModules") { project ->
      val unloadedModuleName = "imported"
      val moduleManager = ModuleManager.getInstance(project)
      val moduleDescription = moduleManager.getUnloadedModuleDescription(unloadedModuleName)
      assertThat(moduleDescription).isNotNull
      val contentRoots = moduleDescription!!.contentRoots
      assertThat(contentRoots.size).isEqualTo(1)
      assertThat(contentRoots[0].fileName).isEqualTo(unloadedModuleName)
    }
  }

  @Test
  fun `load libraries`() = loadProjectAndCheckResults("libraries") { project ->
    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    runInEdtAndWait {
      UIUtil.dispatchAllInvocationEvents()
    }
    val libraries = libraryTable.libraries.sortedBy { it.name }
    assertThat(libraries).hasSize(2)
    val (imported, regular) = libraries
    assertThat(imported.name).isEqualTo("imported")
    assertThat(regular.name).isEqualTo("regular")
    assertThat(imported.externalSource!!.id).isEqualTo("test")
    assertThat(regular.externalSource).isNull()
  }

  @Test
  fun `save artifacts`() = saveProjectInExternalStorageAndCheckResult("artifacts") { project, projectDir ->
    val model = ArtifactManager.getInstance(project).createModifiableModel()
    val regular = model.addArtifact("regular", PlainArtifactType.getInstance())
    regular.outputPath = projectDir.resolve("out/artifacts/regular").systemIndependentPath
    val root = PackagingElementFactory.getInstance().createArchive("a.jar")
    val imported = model.addArtifact("imported", PlainArtifactType.getInstance(), root, externalSource)
    imported.outputPath = projectDir.resolve("out/artifacts/imported").systemIndependentPath
    model.commit()
  }

  @Test
  fun `load artifacts`() = loadProjectAndCheckResults("artifacts") { project ->
    val artifacts = runReadAction { ArtifactManager.getInstance(project).sortedArtifacts }
    assertThat(artifacts).hasSize(2)
    val (imported, regular) = artifacts
    assertThat(imported.name).isEqualTo("imported")
    assertThat(regular.name).isEqualTo("regular")
    assertThat(imported.externalSource!!.id).isEqualTo("test")
    assertThat(regular.externalSource).isNull()
    assertThat(imported.outputPath).isEqualTo("${project.basePath}/out/artifacts/imported")
    assertThat(regular.outputPath).isEqualTo("${project.basePath}/out/artifacts/regular")
    assertThat((imported.rootElement as ArchivePackagingElement).name).isEqualTo("a.jar")
    assertThat(regular.rootElement).isInstanceOf(ArtifactRootElement::class.java)
  }

  @Test
  fun `mark module as mavenized`() {
    loadModifySaveAndCheck("singleRegularModule", "singleModuleAfterMavenization") { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      ExternalSystemModulePropertyManager.getInstance(module).setMavenized(true)
    }
  }

  @Test
  fun `mark module with regular facet as mavenized`() {
    loadModifySaveAndCheck("singleRegularModule", "regularFacetInImportedModule") { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      addFacet(module, null, "regular")
      ExternalSystemModulePropertyManager.getInstance(module).setMavenized(true)
    }
  }

  @Test
  fun `remove regular module with enabled external storage`() {
    loadModifySaveAndCheck("twoRegularModules", "twoRegularModulesAfterRemoval") { project ->
      val moduleManager = ModuleManager.getInstance(project)
      val module = moduleManager.findModuleByName("test2")
      assertThat(module).isNotNull
      runWriteActionAndWait {
        moduleManager.disposeModule(module!!)
      }
    }
  }

  @Test
  fun `change storeExternally property and save libraries to internal storage`() {
    loadModifySaveAndCheck("librariesInExternalStorage", "librariesAfterStoreExternallyPropertyChanged") { project ->
      ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(false)
    }
  }

  @Test
  fun `change storeExternally property several times`() {
    loadModifySaveAndCheck("librariesInExternalStorage", "librariesAfterStoreExternallyPropertyChanged") { project ->
      ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(false)
      runBlocking { project.stateStore.save() }
      ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(true)
      runBlocking { project.stateStore.save() }
      ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(false)
    }
  }

  @Test
  fun `remove library stored externally`() {
    loadModifySaveAndCheck("librariesInExternalStorage", "singleLibraryInExternalStorage") { project ->
      val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
      runWriteActionAndWait {
        libraryTable.removeLibrary(libraryTable.getLibraryByName("spring")!!)
        libraryTable.removeLibrary(libraryTable.getLibraryByName("kotlin")!!)
      }
    }
  }

  @Test
  fun `clean up iml file if we start store project model at external storage`() {
    loadModifySaveAndCheck("singleModule", "singleModuleAfterStoreExternallyPropertyChanged") { project ->
      ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(false)
      runBlocking { project.stateStore.save() }
      ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(true)
    }
  }

  @Test
  fun `test facet and libraries saved in internal store after IDE reload`() {
    loadModifySaveAndCheck("singleModuleFacetAndLibFromExternalSystemInInternalStorage", "singleModuleFacetAndLibFromExternalSystem") { project ->
      ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(true)
    }
  }

  @Test
  fun `clean up facet tag in iml file if we start store project model at external storage`() {
    loadModifySaveAndCheck("importedFacetInImportedModule", "importedFacetAfterStoreExternallyPropertyChanged") { project ->
      ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(false)
      runBlocking { project.stateStore.save() }
      ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(true)
    }
  }

  @Test
  fun `clean up external_build_system at saving data at idea folder`() {
    loadModifySaveAndCheck("singleModuleWithLibrariesInInternalStorage", "singleModuleWithLibrariesInInternalStorage") { project ->
      ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(true)
      runBlocking { project.stateStore.save() }
      ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(false)
    }
  }

  @Test
  fun `check project model saved correctly at internal storage`() {
    loadModifySaveAndCheck("twoModulesWithLibsAndFacetsInExternalStorage", "twoModulesWithLibrariesAndFacets") { project ->
      ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(false)
    }
  }

  @Test
  fun `check project model saved correctly at internal storage after misc manual modification`() {
    loadModifySaveAndCheck("twoModulesWithLibsAndFacetsInExternalStorage", "twoModulesWithLibrariesAndFacets") { project ->
      val miscFile = File(project.projectFilePath!!)
      miscFile.writeText("""
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="ProjectRootManager" version="2" languageLevel="JDK_1_8" />
        </project>
      """.trimIndent())
      WriteAction.runAndWait<RuntimeException> {
        VfsUtil.markDirtyAndRefresh(false, false, false, miscFile)
      }
      runBlocking { StoreReloadManager.getInstance().reloadChangedStorageFiles() }
      ApplicationManager.getApplication().invokeAndWait {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      }
    }
  }

  @Test
  fun `check project model saved correctly at external storage after misc manual modification`() {
    loadModifySaveAndCheck("twoModulesWithLibrariesAndFacets", "twoModulesInExtAndLibsAndFacetsInInternalStorage") { project ->
      val miscFile = File(project.projectFilePath!!)
      miscFile.writeText("""
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="ExternalStorageConfigurationManager" enabled="true" />
          <component name="ProjectRootManager" version="2" languageLevel="JDK_1_8" />
        </project>
      """.trimIndent())
      WriteAction.runAndWait<RuntimeException> {
        VfsUtil.markDirtyAndRefresh(false, false, false, miscFile)
      }
      runBlocking { StoreReloadManager.getInstance().reloadChangedStorageFiles() }
      ApplicationManager.getApplication().invokeAndWait{
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      }
    }
  }

  @Test
  fun `external-system-id attributes are not removed from libraries, artifacts and facets on save`() {
    loadModifySaveAndCheck("elementsWithExternalSystemIdAttributes", "elementsWithExternalSystemIdAttributes") { project ->
      JpsProjectModelSynchronizer.getInstance(project).markAllEntitiesAsDirty()
    }
  }

  @Test
  fun `incorrect modules setup`() {
    suppressLogs {
      loadProjectAndCheckResults("incorrectModulesSetupDifferentIml") { project ->
        val modules = ModuleManager.getInstance(project).modules
        assertEquals(1, modules.size)
      }
    }
  }

  @Test
  fun `incorrect modules setup same iml`() {
      loadProjectAndCheckResults("incorrectModulesSetupSameIml") { project ->
        val modules = ModuleManager.getInstance(project).modules
        assertEquals(1, modules.size)
      }
  }

  @Test
  fun `incorrect modules setup with facet`() {
    suppressLogs {
      loadProjectAndCheckResults("incorrectModulesSetupWithFacet") { project ->
        val modules = ModuleManager.getInstance(project).modules
        assertEquals(1, modules.size)
        val facets = FacetManager.getInstance(modules.single()).allFacets
        assertEquals(1, facets.size)
      }
    }
  }

  @Test
  fun `duplicating library in internal storage`() {
    loadModifySaveAndCheck("duplicatingLibrariesInInternalStorage", "librariesInExternalStorage") {
      val libraries = LibraryTablesRegistrar.getInstance().getLibraryTable(it).libraries
      assertThat(libraries.size).isEqualTo(3)
    }
  }

  @Test
  fun `multiple libraries in internal storage`() {
    loadModifySaveAndCheck("multipleLibrariesInInternalStorage", "multipleLibrariesInInternalStorageFixed") {
      val libraries = LibraryTablesRegistrar.getInstance().getLibraryTable(it).libraries
      assertThat(libraries.size).isEqualTo(1)
    }
  }

  @Test
  fun `multiple modules with the same name`() {
    suppressLogs {
      loadModifySaveAndCheck("multipleModulesWithSameName", "multipleModulesWithSameNameFixed") {
        val modules = ModuleManager.getInstance(it).modules
        assertThat(modules.size).isEqualTo(1)
      }
    }
  }

  @Test
  fun `multiple modules with the same name but different case`() {
    assumeFalse(SystemInfo.isFileSystemCaseSensitive)
    suppressLogs {
      loadModifySaveAndCheck("multipleModulesWithSameNameDifferentCase", "multipleModulesWithSameNameDifferentCaseFixed") {
        val modules = ModuleManager.getInstance(it).modules
        val module = assertOneElement(modules)
        assertEquals("test", module.name)
      }
    }
  }

  @Test
  fun `save regular and imported facets and sub-facets`() {
    loadModifySaveAndCheck("singleModule", "singleModuleWithSubFacets") { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      createFacetAndSubFacet(module, "web", null, null)
      createFacetAndSubFacet(module, "spring", MOCK_EXTERNAL_SOURCE, MOCK_EXTERNAL_SOURCE)
    }
  }

  @Test
  fun `load regular and imported facets and sub-facets`() = loadProjectAndCheckResults("singleModuleWithSubFacets") { project ->
    val module = ModuleManager.getInstance(project).modules.single()
    checkFacetAndSubFacet(module, "web", null, null)
    checkFacetAndSubFacet(module, "spring", MOCK_EXTERNAL_SOURCE, MOCK_EXTERNAL_SOURCE)
  }

  @Test
  fun `imported facet and regular sub-facet`() {
    loadModifySaveAndCheck("singleModule", "singleModuleWithRegularSubFacet") { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      createFacetAndSubFacet(module, "spring", MOCK_EXTERNAL_SOURCE, null)
    }
  }

  @Test
  fun `load imported facet and regular sub-facet`() = loadProjectAndCheckResults("singleModuleWithRegularSubFacet") { project ->
    val module = ModuleManager.getInstance(project).modules.single()
    checkFacetAndSubFacet(module, "spring", MOCK_EXTERNAL_SOURCE, null)
  }

  @Test
  fun `regular facet and imported sub-facet`() {
    loadModifySaveAndCheck("singleModule", "singleModuleWithImportedSubFacet") { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      createFacetAndSubFacet(module, "web", null, MOCK_EXTERNAL_SOURCE)
    }
  }

  @Test
  fun `load regular facet and imported sub-facet`() = loadProjectAndCheckResults("singleModuleWithImportedSubFacet") { project ->
    val module = ModuleManager.getInstance(project).modules.single()
    checkFacetAndSubFacet(module, "web", null, MOCK_EXTERNAL_SOURCE)
  }

  @Test
  fun `load module with test properties`() = loadProjectAndCheckResults("moduleWithTestProperties") { project ->
    val mainModuleName = "foo"
    val testModuleName = "foo.test"
    val moduleManager = ModuleManager.getInstance(project)
    val testModule = moduleManager.findModuleByName(testModuleName)
    val mainModule = moduleManager.findModuleByName(mainModuleName)
    assertNotNull(testModule)
    assertNotNull(mainModule)
    val testModuleProperties = TestModuleProperties.getInstance(testModule!!)
    assertEquals(mainModuleName, testModuleProperties.productionModuleName)
    assertSame(mainModule, testModuleProperties.productionModule)
  }

  @Test
  fun `test property for module`() {
    loadModifySaveAndCheck("twoModules", "moduleWithTestProperties") {project ->
      val mainModuleName = "foo"
      val testModuleName = "foo.test"
      val moduleManager = ModuleManager.getInstance(project)
      val testModule = moduleManager.findModuleByName(testModuleName)
      assertNotNull(testModule)
      val testModuleProperties = TestModuleProperties.getInstance(testModule!!)
      runWriteActionAndWait {
        testModuleProperties.productionModuleName = mainModuleName
      }
    }
  }

  @Test(expected = Test.None::class)
  fun `get modifiable models of renamed module`() = loadProjectAndCheckResults("singleModuleWithImportedSubFacet") { project ->
    runWriteActionAndWait {
      val newModule = ModuleManager.getInstance(project).newModule("myModule", EmptyModuleType.EMPTY_MODULE)

      val provider = IdeModifiableModelsProviderImpl(project)

      val anotherModifiableModel = provider.modifiableModuleModel
      anotherModifiableModel.renameModule(newModule, "newName")

      // Assert no exceptions
      provider.getModifiableRootModel(newModule)

      anotherModifiableModel.dispose()
    }
  }

  private fun createFacetAndSubFacet(module: Module, name: String, facetSource: ProjectModelExternalSource?,
                                     subFacetSource: ProjectModelExternalSource?) {
    val facetManager = FacetManager.getInstance(module)
    val modifiableFacetModel = facetManager.createModifiableModel()

    val parentFacet = MockFacet(module, name)
    modifiableFacetModel.addFacet(parentFacet, facetSource)
    parentFacet.configuration.data = "1"

    val subFacet = Facet(MockSubFacetType.getInstance(), module, "sub_$name", MockFacetConfiguration(), parentFacet)
    modifiableFacetModel.addFacet(subFacet, subFacetSource)
    subFacet.configuration.data = "2"

    WriteAction.runAndWait<RuntimeException> { modifiableFacetModel.commit() }
    (facetManager as FacetManagerBase).checkConsistency()
  }

  private fun checkFacetAndSubFacet(module: Module, name: String, facetSource: ProjectModelExternalSource?,
                                    subFacetSource: ProjectModelExternalSource?) {
    val facetManager = FacetManager.getInstance(module)
    val parentFacet = facetManager.findFacet(MockFacetType.ID, name)
    assertNotNull(parentFacet)
    assertEquals("1", parentFacet!!.configuration.data)
    if (facetSource != null) assertEquals(facetSource.id, parentFacet.externalSource!!.id)

    val subFacet = facetManager.findFacet(MockSubFacetType.ID, "sub_$name")
    assertNotNull(subFacet)
    assertEquals("2", (subFacet!!.configuration as MockFacetConfiguration).data)
    if (subFacetSource != null) assertEquals(subFacetSource.id, subFacet.externalSource!!.id)
  }

  @Before
  fun registerFacetType() {
    WriteAction.runAndWait<RuntimeException> {
      FacetType.EP_NAME.point.registerExtension(MockFacetType(), disposableRule.disposable)
      FacetType.EP_NAME.point.registerExtension(MockSubFacetType(), disposableRule.disposable)
    }
  }

  private val externalSource get() = ExternalProjectSystemRegistry.getInstance().getSourceById("test")

  private fun saveProjectInInternalStorageAndCheckResult(testDataDirName: String, setupProject: (Project, Path) -> Unit) {
    doNotEnableExternalStorageByDefaultInTests {
      saveProjectAndCheckResult(testDataDirName, false, setupProject)
    }
  }

  private fun saveProjectInExternalStorageAndCheckResult(testDataDirName: String, setupProject: (Project, Path) -> Unit) {
    saveProjectAndCheckResult(testDataDirName, true, setupProject)
  }

  private fun saveProjectAndCheckResult(testDataDirName: String,
                                        storeExternally: Boolean,
                                        setupProject: (Project, Path) -> Unit) {
    runBlocking {
      createProjectAndUseInLoadComponentStateMode(tempDirManager, directoryBased = true, useDefaultProjectSettings = false) { project ->
        ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(storeExternally)
        val projectDir = project.stateStore.directoryStorePath!!.parent
        val cacheDir = ExternalProjectsDataStorage.getProjectConfigurationDir(project)
        cacheDir.delete()

        runBlocking {
          withContext(Dispatchers.EDT) {
            ApplicationManager.getApplication().runWriteAction {
              //we need to set language level explicitly because otherwise if some tests modifies language level in the default project, we'll
              // get different content in misc.xml
              LanguageLevelProjectExtension.getInstance(project)!!.languageLevel = LanguageLevel.JDK_1_8
              setupProject(project, projectDir)
            }
          }
        }

        saveAndCompare(project, testDataDirName)
      }
    }
  }

  private fun saveAndCompare(project: Project, dataDirNameToCompareWith: String) {
    val cacheDir = ExternalProjectsDataStorage.getProjectConfigurationDir(project)
    Disposer.register(disposableRule.disposable, Disposable { cacheDir.delete() })

    runBlocking {
      project.stateStore.save()
    }

    val expectedDir = tempDirManager.newPath("expectedStorage")
    FileUtil.copyDir(testDataRoot.resolve("common").toFile(), expectedDir.toFile())
    FileUtil.copyDir(testDataRoot.resolve(dataDirNameToCompareWith).toFile(), expectedDir.toFile())

    val projectDir = project.stateStore.directoryStorePath!!.parent
    projectDir.toFile().assertMatches(directoryContentOf(expectedDir.resolve("project")))

    val expectedCacheDir = expectedDir.resolve("cache")
    if (Files.exists(expectedCacheDir)) {
      cacheDir.toFile().assertMatches(directoryContentOf(expectedCacheDir), FileTextMatcher.ignoreBlankLines())
    }
    else {
      assertTrue("$cacheDir doesn't exist", !Files.exists(cacheDir) || isFolderWithoutFiles(cacheDir.toFile()))
    }
  }

  private fun loadModifySaveAndCheck(dataDirNameToLoad: String, dataDirNameToCompareWith: String, modifyProject: (Project) -> Unit) {
    loadProjectAndCheckResults(dataDirNameToLoad) { project ->
      modifyProject(project)
      saveAndCompare(project, dataDirNameToCompareWith)
    }
  }

  private val testDataRoot
    get() = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("platform/external-system-impl/testData/jpsSerialization")

  private fun loadProjectAndCheckResults(testDataDirName: String, checkProject: (Project) -> Unit) {
    @Suppress("RedundantSuspendModifier")
    fun copyProjectFiles(dir: VirtualFile): Path {
      val projectDir = dir.toNioPath()
      FileUtil.copyDir(testDataRoot.resolve("common/project").toFile(), projectDir.toFile())
      val testProjectFilesDir = testDataRoot.resolve(testDataDirName).resolve("project").toFile()
      if (testProjectFilesDir.exists()) {
        FileUtil.copyDir(testProjectFilesDir, projectDir.toFile())
      }
      val testCacheFilesDir = testDataRoot.resolve(testDataDirName).resolve("cache").toFile()
      if (testCacheFilesDir.exists()) {
        val cachePath = appSystemDir.resolve("external_build_system").resolve(getProjectCacheFileName(dir.toNioPath()))
        FileUtil.copyDir(testCacheFilesDir, cachePath.toFile())
      }
      VfsUtil.markDirtyAndRefresh(false, true, true, dir)
      return projectDir
    }
    doNotEnableExternalStorageByDefaultInTests {
      runBlocking {
        createOrLoadProject(tempDirManager, ::copyProjectFiles, loadComponentState = true, useDefaultProjectSettings = false) {
          checkProject(it)
        }
      }
    }
  }

  private fun isFolderWithoutFiles(root: File): Boolean = root.walk().none { it.isFile }

  private fun suppressLogs(action: () -> Unit) {
    LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> =
        if (message.contains("Trying to load multiple modules with the same name.")) Action.NONE
        else Action.ALL
    }) {
      action()
    }
  }
}


private val MOCK_EXTERNAL_SOURCE = object: ProjectModelExternalSource {
  override fun getDisplayName() = "mock"

  override fun getId() = "mock"
}

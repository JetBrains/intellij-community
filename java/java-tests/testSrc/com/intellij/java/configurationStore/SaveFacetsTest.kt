// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.configurationStore

import com.intellij.facet.FacetManager
import com.intellij.facet.mock.MockFacetConfiguration
import com.intellij.facet.mock.MockFacetType
import com.intellij.facet.mock.registerFacetType
import com.intellij.facet.mock.runWithRegisteredFacetTypes
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.CustomModuleEntitySource
import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.jps.entities.ModuleCustomImlDataEntity
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.modifyEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.testFramework.workspaceModel.updateProjectModel
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import com.intellij.workspaceModel.ide.getJpsProjectConfigLocation
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class SaveFacetsTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Rule
  @JvmField
  val disposable = DisposableRule()

  @Before
  fun setUp() {
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(disposable.disposable, {})
  }

  @Test
  fun `single facet`() {
    registerFacetType(MockFacetType(), disposable.disposable)
    val module = projectModel.createModule("foo")
    projectModel.addFacet(module, MockFacetType.getInstance(), MockFacetConfiguration("my-data"))
    projectModel.saveProjectState()
    projectModel.baseProjectDir.root.assertMatches(directoryContentOf(configurationStoreTestDataRoot.resolve("single-facet")))
  }

  @Test
  fun `single invalid facet`() {
    val module = projectModel.createModule("foo")
    runWithRegisteredFacetTypes(MockFacetType()) {
      projectModel.addFacet(module, MockFacetType.getInstance(), MockFacetConfiguration("my-data"))
    }
    projectModel.saveProjectState()
    projectModel.baseProjectDir.root.assertMatches(directoryContentOf(configurationStoreTestDataRoot.resolve("single-facet")))
  }

  @Test
  fun `facet in module with custom storage`() {
    class SampleCustomModuleSource(override val internalSource: JpsFileEntitySource) : EntitySource, CustomModuleEntitySource
    val workspaceModel = WorkspaceModel.getInstance(projectModel.project)
    val moduleDir = projectModel.baseProjectDir.virtualFileRoot.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())
    val source = SampleCustomModuleSource(
      JpsProjectFileEntitySource.FileInDirectory(moduleDir, getJpsProjectConfigLocation(projectModel.project)!!))
    runWriteActionAndWait {
      workspaceModel.updateProjectModel {
        val moduleEntity = it addEntity ModuleEntity("foo", listOf(ModuleDependencyItem.ModuleSourceDependency), source)
        it addEntity ModuleCustomImlDataEntity(HashMap(mapOf(JpsProjectLoader.CLASSPATH_ATTRIBUTE to SampleCustomModuleRootsSerializer.ID)),
                                               source) {
          module = moduleEntity
        }
        moduleEntity
      }
    }
    val module = projectModel.moduleManager.findModuleByName("foo")!!
    registerFacetType(MockFacetType(), disposable.disposable)
    projectModel.addFacet(module, MockFacetType.getInstance(), MockFacetConfiguration("my-data"))
    projectModel.saveProjectState()
    projectModel.baseProjectDir.root.assertMatches(directoryContentOf(configurationStoreTestDataRoot.resolve("facet-in-module-with-custom-storage")))

    runWriteActionAndWait {
      workspaceModel.updateProjectModel {
        val moduleEntity = it.entities(ModuleEntity::class.java).single()
        it.modifyEntity(moduleEntity) {
          dependencies = mutableListOf(ModuleDependencyItem.ModuleSourceDependency, ModuleDependencyItem.InheritedSdkDependency)
        }
      }
    }

    projectModel.saveProjectState()
    projectModel.baseProjectDir.root.assertMatches(directoryContentOf(configurationStoreTestDataRoot.resolve("facet-in-module-with-custom-storage")))

    runWriteActionAndWait {
      val facet = FacetManager.getInstance(module).getFacetByType(MockFacetType.ID)!!
      facet.configuration.data = "changed"
      FacetManager.getInstance(module).facetConfigurationChanged(facet)
    }

    projectModel.saveProjectState()
    projectModel.baseProjectDir.root.assertMatches(directoryContentOf(configurationStoreTestDataRoot.resolve("facet-in-module-with-custom-storage-changed")))
  }

  @Test
  fun `remove last facet`() {
    registerFacetType(MockFacetType(), disposable.disposable)
    val module = projectModel.createModule("foo")
    val facet = projectModel.addFacet(module, MockFacetType.getInstance(), MockFacetConfiguration("my-data"))
    projectModel.saveProjectState()
    projectModel.removeFacet(facet)
    projectModel.saveProjectState()
    projectModel.baseProjectDir.root.assertMatches(directoryContentOf(configurationStoreTestDataRoot.resolve("single-module")))
  }

}
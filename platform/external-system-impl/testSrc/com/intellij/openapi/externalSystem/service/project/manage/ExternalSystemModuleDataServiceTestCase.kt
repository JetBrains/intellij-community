// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.Function
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

@TestApplication
abstract class ExternalSystemModuleDataServiceTestCase {

  val project by projectFixture()
  val projectPath get() = project.basePath!!
  val projectRoot get() = Path.of(projectPath)

  val Module.contentRoots: List<String>
    get() = ModuleRootManager.getInstance(this).contentRoots.map { it.path }

  fun createManager(systemId: ProjectSystemId, vararg projectPaths: String): ExternalSystemManager<*, *, *, *, *> {
    val projectSettings = projectPaths.map { projectPath ->
      mock<ExternalProjectSettings>().also {
        whenever(it.externalProjectPath).thenReturn(projectPath)
      }
    }
    val settings = mock<AbstractExternalSystemSettings<*, *, *>>().also {
      whenever(it.linkedProjectsSettings).thenReturn(projectSettings)
    }
    val localSettings = mock<AbstractExternalSystemLocalSettings<*>>()
    return mock<ExternalSystemManager<*, *, *, *, *>>().also {
      whenever(it.systemId).thenReturn(systemId)
      whenever(it.settingsProvider).thenReturn(Function { settings })
      whenever(it.localSettingsProvider).thenReturn(Function { localSettings })
    }
  }

  fun createModelsProvider(disposable: Disposable): IdeModifiableModelsProvider {
    val modelsProvider = IdeModifiableModelsProviderImpl(project)
    disposable.whenDisposed {
      runBlocking {
        writeAction {
          modelsProvider.dispose()
        }
      }
    }
    return modelsProvider
  }

  fun importModuleData(modelsProvider: IdeModifiableModelsProvider, projectData: ProjectData, moduleNodes: List<DataNode<ModuleData>>) {
    val dataService = ModuleDataService()
    dataService.importData(moduleNodes, projectData, project, modelsProvider)
    val orphanData = dataService.computeOrphanData(moduleNodes, projectData, project, modelsProvider)
    dataService.removeData(orphanData, emptyList(), projectData, project, modelsProvider)
  }

  fun assertModules(modelsProvider: IdeModifiableModelsProvider, expected: List<String>) {
    CollectionAssertions.assertEqualsUnordered(expected, modelsProvider.modules.map { it.name })
  }

  fun assertContentRoots(modelsProvider: IdeModifiableModelsProvider, moduleName: String, expected: List<String>) {
    val module = modelsProvider.findIdeModule(moduleName) ?: error("Cannot find $moduleName module in models provider")
    CollectionAssertions.assertEqualsUnordered(expected, module.contentRoots)
  }

  fun assertModuleOptions(
    modelsProvider: IdeModifiableModelsProvider, moduleName: String,
    systemId: ProjectSystemId?, rootProjectPath: String?, linkedProjectPath: String?, linkedProjectId: String?
  ) {
    val module = modelsProvider.findIdeModule(moduleName) ?: error("Cannot find $moduleName module in models provider")
    val externalModuleOptions = ExternalSystemModulePropertyManager.getInstance(module)
    Assertions.assertEquals(systemId?.id, externalModuleOptions.getExternalSystemId())
    Assertions.assertEquals(rootProjectPath, externalModuleOptions.getRootProjectPath())
    Assertions.assertEquals(linkedProjectPath, externalModuleOptions.getLinkedProjectPath())
    Assertions.assertEquals(linkedProjectId, externalModuleOptions.getLinkedProjectId())
  }
}
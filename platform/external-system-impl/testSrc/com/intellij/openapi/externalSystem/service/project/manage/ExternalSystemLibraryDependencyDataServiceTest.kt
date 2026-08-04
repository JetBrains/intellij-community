// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData
import com.intellij.openapi.externalSystem.model.project.LibraryLevel
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.outputStream

/**
 * Regression tests for IJPL-242196: module-level library dependencies used to only get new roots added on reimport,
 * so roots that the external system no longer reports were kept forever.
 */
class ExternalSystemLibraryDependencyDataServiceTest : ExternalSystemModuleDataServiceTestCase() {

  private val systemId = ProjectSystemId("systemId")
  private val libraryName = "test:demo:1.0"
  private val moduleName = "module"

  @Test
  fun `test stale binary root is removed from module level library on reimport`(): Unit = runBlocking {
    registerExternalSystemManager(asDisposable())

    val resolvableJar = createResolvableJar()
    val staleJar = projectRoot.resolve("lib/stale.jar")

    syncLibraryDependency(asDisposable(), binaryPaths = listOf(resolvableJar, staleJar))

    assertLibraryPaths(OrderRootType.CLASSES, listOf(resolvableJar, staleJar))

    syncLibraryDependency(asDisposable(), binaryPaths = listOf(resolvableJar))

    assertLibraryPaths(OrderRootType.CLASSES, listOf(resolvableJar))
  }

  @Test
  fun `test stale source root is removed from module level library on reimport`(): Unit = runBlocking {
    registerExternalSystemManager(asDisposable())

    val resolvableJar = createResolvableJar()
    val staleSourcesJar = projectRoot.resolve("lib/resolvable-old-sources.jar")
    val sourcesJar = projectRoot.resolve("lib/resolvable-sources.jar")

    syncLibraryDependency(asDisposable(), binaryPaths = listOf(resolvableJar), sourcePaths = listOf(staleSourcesJar))

    assertLibraryPaths(OrderRootType.SOURCES, listOf(staleSourcesJar))

    syncLibraryDependency(asDisposable(), binaryPaths = listOf(resolvableJar), sourcePaths = listOf(sourcesJar))

    assertLibraryPaths(OrderRootType.CLASSES, listOf(resolvableJar))
    assertLibraryPaths(OrderRootType.SOURCES, listOf(sourcesJar))
  }

  @Test
  fun `test source roots are preserved when external system reports none`(): Unit = runBlocking {
    registerExternalSystemManager(asDisposable())

    val resolvableJar = createResolvableJar()
    val sourcesJar = projectRoot.resolve("lib/resolvable-sources.jar")

    syncLibraryDependency(asDisposable(), binaryPaths = listOf(resolvableJar), sourcePaths = listOf(sourcesJar))

    assertLibraryPaths(OrderRootType.SOURCES, listOf(sourcesJar))

    syncLibraryDependency(asDisposable(), binaryPaths = listOf(resolvableJar))

    // sources attached by the user or by a separate "download sources" action must survive a reimport
    assertLibraryPaths(OrderRootType.SOURCES, listOf(sourcesJar))
  }

  private fun registerExternalSystemManager(disposable: Disposable) {
    val manager = createManager(systemId, projectPath)
    ExternalSystemManager.EP_NAME.point.registerExtension(manager, disposable)
  }

  /**
   * Performs a full external system sync round for a single module-level library dependency and commits its result,
   * so that the next call observes it as the already existing IDE state.
   */
  private suspend fun syncLibraryDependency(
    disposable: Disposable,
    binaryPaths: List<Path>,
    sourcePaths: List<Path> = emptyList(),
  ) {
    val projectData = ProjectData(systemId, "project", projectPath, projectPath)
    val moduleData = ModuleData(moduleName, systemId, "moduleType", moduleName,
                                "$projectPath/$moduleName.iml", "$projectPath/$moduleName")

    val libraryData = LibraryData(systemId, libraryName)
    for (path in binaryPaths) {
      libraryData.addPath(LibraryPathType.BINARY, path.toString())
    }
    for (path in sourcePaths) {
      libraryData.addPath(LibraryPathType.SOURCE, path.toString())
    }

    val projectNode = DataNode(ProjectKeys.PROJECT, projectData, null)
    val moduleNode = projectNode.createChild(ProjectKeys.MODULE, moduleData)
    val dependencyData = LibraryDependencyData(moduleData, libraryData, LibraryLevel.MODULE)
    val dependencyNodes = listOf(moduleNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, dependencyData))

    val modelsProvider = createModelsProvider(disposable)
    importModuleData(modelsProvider, projectData, listOf(moduleNode))

    val dataService = LibraryDependencyDataService()
    dataService.importData(dependencyNodes, projectData, project, modelsProvider)
    val orphanData = dataService.computeOrphanData(dependencyNodes, projectData, project, modelsProvider)
    dataService.removeData(orphanData, dependencyNodes, projectData, project, modelsProvider)

    edtWriteAction { modelsProvider.commit() }
  }

  private suspend fun assertLibraryPaths(rootType: OrderRootType, expected: List<Path>) {
    val actual = readAction {
      val module = ModuleManager.getInstance(project).findModuleByName(moduleName)
                   ?: error("Cannot find $moduleName module")
      val libraryEntry = ModuleRootManager.getInstance(module).orderEntries
                           .filterIsInstance<LibraryOrderEntry>()
                           .singleOrNull()
                         ?: error("Expected a single library dependency in the $moduleName module")
      libraryEntry.getRootUrls(rootType).map(::toLocalPath)
    }
    assertThat(actual).containsExactlyInAnyOrderElementsOf(expected.map { it.invariantSeparatorsPathString })
  }

  private fun createResolvableJar(): Path {
    val path = projectRoot.resolve("lib/resolvable.jar")
    path.parent.createDirectories()
    ZipOutputStream(path.outputStream()).use { output ->
      output.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
      output.closeEntry()
    }
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
    return path
  }

  private fun toLocalPath(url: String): String {
    return VfsUtilCore.urlToPath(url.removeSuffix(JarFileSystem.JAR_SEPARATOR))
  }
}

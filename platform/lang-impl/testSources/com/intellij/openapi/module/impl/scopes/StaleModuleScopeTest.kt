// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.libraries.LibraryContext
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryEntityBuilder
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.psi.search.ActualCodeInsightContextInfo
import com.intellij.psi.search.ActualContextFileInfo
import com.intellij.psi.search.CodeInsightContextAwareSearchScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.impl.VirtualFileEnumeration
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A module scope that a caller keeps across a root change, while the change recreates a library or a module with
 * the same id and the same roots.
 *
 * The platform clears its own scope cache on a root change, but a plugin cache can keep the old scope object.
 * `ModuleWithDependenciesScope.equals` compares only the module and the options, so a new scope also finds the
 * key of the old one. The old scope must then answer as a new one does: the order entry names its library or its
 * module, not one bridge instance of it.
 */
@TestApplication
internal class StaleModuleScopeTest {
  // Per test, not shared: both tests create entities with the same names.
  private val project = projectFixture(openAfterCreation = true).withSharedSourceEnabled()
  private val rootDir = tempPathFixture()

  @Test
  fun `a scope kept across the recreation of its library still contains the library`(): Unit = timeoutRunBlocking {
    val shape = createShape()
    val scope = readAction { GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(shape.user()) }
    readAction { assertAnswers(scope, shape.libraryFile, shape.outsideFile) }

    // What a gem refresh does: one update removes the library, the next one adds it again.
    update(shape, "remove the library") { storage ->
      storage.removeEntity(storage.resolve(LIBRARY_ID)!!)
    }
    update(shape, "add the library again") { storage ->
      storage.addEntity(libraryEntity(shape.libraryRoot))
    }

    readAction {
      assertAnswers(scope, shape.libraryFile, shape.outsideFile)
      val library = LibraryTablesRegistrar.getInstance().getLibraryTable(project.get()).getLibraryByName(LIBRARY_ID.name)
      val contexts = contextsOf(scope, shape.libraryFile)
      assertEquals(listOf(library), contexts.map { (it as LibraryContext).getLibrary() }) {
        "the context must name the library that exists now"
      }
    }
  }

  @Test
  fun `a scope kept across the recreation of a module it depends on still contains that module`(): Unit = timeoutRunBlocking {
    val shape = createShape()
    val scope = readAction { GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(shape.user()) }
    readAction { assertAnswers(scope, shape.dependencyFile, shape.outsideFile) }

    update(shape, "remove the dependency module") { storage ->
      storage.removeEntity(storage.resolve(ModuleId(DEPENDENCY_MODULE))!!)
    }
    update(shape, "add the dependency module again") { storage ->
      storage.addEntity(moduleEntity(DEPENDENCY_MODULE, shape.dependencyRoot, listOf(ModuleSourceDependency)))
    }

    readAction { assertAnswers(scope, shape.dependencyFile, shape.outsideFile) }
  }

  /**
   * The questions an index query and a resolve ask: the file enumeration, which is where the disposed library
   * threw, `contains`, and `compare` against a file under no root.
   */
  private fun assertAnswers(scope: GlobalSearchScope, inScope: VirtualFile, outside: VirtualFile) {
    VirtualFileEnumeration.extract(scope)
    assertTrue(scope.contains(inScope)) { "$inScope is in $scope" }
    assertTrue(scope.compare(inScope, outside) > 0) { "$inScope ranks before a file that $scope does not contain" }
  }

  private fun contextsOf(scope: GlobalSearchScope, file: VirtualFile): Collection<*> {
    val info = ((scope as CodeInsightContextAwareSearchScope).codeInsightContextInfo as ActualCodeInsightContextInfo).getFileInfo(file)
    return assertInstanceOf(ActualContextFileInfo::class.java, info).contexts
  }

  private inner class Shape(
    val libraryRoot: VirtualFile,
    val libraryFile: VirtualFile,
    val dependencyRoot: VirtualFile,
    val dependencyFile: VirtualFile,
    val outsideFile: VirtualFile,
  ) {
    fun user(): Module = ModuleManager.getInstance(project.get()).findModuleByName(USER_MODULE)!!
  }

  /** `user` depends on the library `lib` and on the module `dependency`; `outside` is under no root. */
  private suspend fun createShape(): Shape {
    val base = VfsUtil.createDirectories(rootDir.get().resolve("shape-${System.nanoTime()}").toString())
    lateinit var shape: Shape
    edtWriteAction {
      fun dirWithFile(name: String): Pair<VirtualFile, VirtualFile> {
        val dir = base.createChildDirectory(this, name)
        return dir to dir.createChildData(this, "probe.txt")
      }
      val (libraryRoot, libraryFile) = dirWithFile("lib")
      val (dependencyRoot, dependencyFile) = dirWithFile("dependency")
      val (userRoot, _) = dirWithFile("user")
      val (_, outsideFile) = dirWithFile("outside")
      shape = Shape(libraryRoot, libraryFile, dependencyRoot, dependencyFile, outsideFile)

      WorkspaceModel.getInstance(project.get()).updateProjectModel("create the shape") { storage ->
        storage.addEntity(libraryEntity(libraryRoot))
        storage.addEntity(moduleEntity(DEPENDENCY_MODULE, dependencyRoot, listOf(ModuleSourceDependency)))
        storage.addEntity(moduleEntity(USER_MODULE, userRoot, listOf(
          ModuleSourceDependency,
          ModuleDependency(ModuleId(DEPENDENCY_MODULE), exported = false, DependencyScope.COMPILE, productionOnTest = false),
          LibraryDependency(LIBRARY_ID, exported = false, DependencyScope.COMPILE),
        )))
      }
    }
    return shape
  }

  /**
   * Applies [change] in its own model update, then changes the VFS structure. The file enumeration of a scope is
   * computed again only after a VFS structure change, and without one it hides a stale scope behind its cache.
   */
  private suspend fun update(shape: Shape, description: String, change: (MutableEntityStorage) -> Unit) {
    edtWriteAction {
      WorkspaceModel.getInstance(project.get()).updateProjectModel(description, change)
      shape.outsideFile.parent.createChildData(this, "vfs-change-${System.nanoTime()}.txt")
    }
  }

  private fun libraryEntity(root: VirtualFile): LibraryEntityBuilder {
    val urlManager = WorkspaceModel.getInstance(project.get()).getVirtualFileUrlManager()
    return LibraryEntity(LIBRARY_ID.name, LIBRARY_ID.tableId,
                         listOf(LibraryRoot(urlManager.storeAndGet(root.url), LibraryRootTypeId.COMPILED)),
                         NonPersistentEntitySource)
  }

  private fun moduleEntity(name: String, sourceRoot: VirtualFile, dependencies: List<ModuleDependencyItem>): ModuleEntityBuilder {
    val urlManager = WorkspaceModel.getInstance(project.get()).getVirtualFileUrlManager()
    val url = urlManager.storeAndGet(sourceRoot.url)
    return ModuleEntity(name, dependencies, NonPersistentEntitySource) {
      contentRoots = listOf(ContentRootEntity(url, emptyList(), NonPersistentEntitySource) {
        sourceRoots = listOf(SourceRootEntity(url, SourceRootTypeId("java-source"), NonPersistentEntitySource))
      })
    }
  }
}

private const val USER_MODULE = "StaleModuleScopeTest_user"
private const val DEPENDENCY_MODULE = "StaleModuleScopeTest_dependency"
private val LIBRARY_ID = LibraryId("StaleModuleScopeTest_lib", LibraryTableId.ProjectLibraryTableId)

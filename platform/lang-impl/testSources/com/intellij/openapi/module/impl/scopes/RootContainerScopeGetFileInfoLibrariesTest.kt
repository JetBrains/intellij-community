// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.psi.search.ActualCodeInsightContextInfo
import com.intellij.psi.search.ActualContextFileInfo
import com.intellij.psi.search.CodeInsightContextAwareSearchScope
import com.intellij.psi.search.DoesNotContainFileInfo
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path

/**
 * Tests for [RootContainerScope.getFileInfo] focused on library roots.
 *
 * The bug-fix regression case (`library file from a non-depending module returns DoesNotContain`)
 * is the load-bearing assertion for commit `4cfab90` (IDEA-389519): the old code returned
 * `NoContextFileInfo` here, which contradicted `scope.contains(libFile) == false`.
 *
 * Source-root / shared-source cases live in [RootContainerScopeGetFileInfoTest].
 */
@TestApplication
internal class RootContainerScopeGetFileInfoLibrariesTest {
  companion object {
    // RootContainerScope.getFileInfo requires shared-source support — ClassicRootContainer
    // doesn't implement getRootDescriptor(RootDescriptor).
    private val project = projectFixture().withSharedSourceEnabled()
    private val uniq = AtomicInteger()
  }

  @Test
  fun `library file from the owning module's scope returns ActualContextFileInfo with the library context`(): Unit =
    timeoutRunBlocking {
      val p = project.get()
      val tag = uniq.incrementAndGet()
      val module = createModule(p, "rcsgfi_lib_owner_$tag")
      val (libRoot, libFile) = createLibraryRoot(p, "lib_owner_$tag")
      val library = createProjectLibrary(p, "rcsgfi_lib_owner_$tag", libRoot)
      addLibraryDependency(module, library)

      val libContext = readAction {
        ProjectModelContextBridge.getInstance(p).getContext(library)
        ?: error("ProjectModelContextBridge returned null for $library")
      }

      val info = module.contextInfoOfScopeWithLibraries()
      readAction {
        val result = info.getFileInfo(libFile)
        val actual = assertInstanceOf(ActualContextFileInfo::class.java, result)
        assertEquals(setOf(libContext), actual.contexts.toSet())
      }
    }

  @Test
  fun `library file from owning module returns library context even when a non-owning module exists in the project`(): Unit =
    timeoutRunBlocking {
      val p = project.get()
      val tag = uniq.incrementAndGet()
      val moduleA = createModule(p, "rcsgfi_lib_pair_a_$tag")
      val moduleB = createModule(p, "rcsgfi_lib_pair_b_$tag")
      val (libRoot, libFile) = createLibraryRoot(p, "lib_pair_$tag")
      val library = createProjectLibrary(p, "rcsgfi_lib_pair_$tag", libRoot)
      addLibraryDependency(moduleB, library)

      val libContext = readAction {
        ProjectModelContextBridge.getInstance(p).getContext(library)
        ?: error("ProjectModelContextBridge returned null for $library")
      }

      readAction {
        val resultB = moduleB.contextInfoOfScopeWithLibraries().getFileInfo(libFile)
        val actualB = assertInstanceOf(ActualContextFileInfo::class.java, resultB)
        assertEquals(setOf(libContext), actualB.contexts.toSet())

        val resultA = moduleA.contextInfoOfScopeWithLibraries().getFileInfo(libFile)
        assertInstanceOf(DoesNotContainFileInfo::class.java, resultA)
      }
    }
}

private fun Module.contextInfoOfScopeWithLibraries(): ActualCodeInsightContextInfo =
  getModuleWithDependenciesAndLibrariesScope(true).asActualContextInfo()

private fun GlobalSearchScope.asActualContextInfo(): ActualCodeInsightContextInfo {
  val aware = this as CodeInsightContextAwareSearchScope
  return aware.codeInsightContextInfo as ActualCodeInsightContextInfo
}

private suspend fun createModule(project: Project, name: String): Module = edtWriteAction {
  val imlPath = Path(project.basePath ?: error("project basePath is null")).resolve("$name.iml")
  ModuleManager.getInstance(project).newModule(imlPath, "EMPTY_MODULE")
}

private suspend fun createLibraryRoot(project: Project, name: String): Pair<VirtualFile, VirtualFile> {
  val basePath = project.basePath ?: error("project basePath is null")
  val rootPath = Path(basePath).resolve(name)
  val rootVfs = edtWriteAction {
    VfsUtil.createDirectoryIfMissing(rootPath.toString())
    ?: error("Failed to create library root at $rootPath")
  }
  val classFile = edtWriteAction {
    rootVfs.createChildData(null, "Lib.class")
  }
  return rootVfs to classFile
}

private suspend fun createProjectLibrary(project: Project, name: String, classesRoot: VirtualFile): Library =
  edtWriteAction {
    val table = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    val tableModel = table.modifiableModel
    val library = tableModel.createLibrary(name) as LibraryEx
    val libraryModel = library.modifiableModel
    libraryModel.addRoot(classesRoot, OrderRootType.CLASSES)
    libraryModel.commit()
    tableModel.commit()
    library
  }

private suspend fun addLibraryDependency(module: Module, library: Library) {
  edtWriteAction {
    ModuleRootModificationUtil.addDependency(module, library)
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path

/**
 * [ProjectFileIndex.getModuleSourceOrLibraryClassesRoots]: what it reports for the two kinds of owned roots, that
 * the descriptor of a root is computed once rather than on every call, and that the cache cannot outlive the file
 * set it is attached to.
 *
 * The `assertSame` between two calls is the caching contract: the descriptor is stored on the
 * `WorkspaceFileSetImpl`, whose lifetime ends whenever its entity changes, so repeated queries - every multiverse
 * `contains` - must not resolve the owner through the workspace model snapshot again. The library-replacement test
 * is the other half of that contract: a new entity means a new file set, so the stale descriptor must be gone.
 */
@TestApplication
internal class ModuleSourceOrLibraryClassesRootDescriptorsTest {
  companion object {
    private val project = projectFixture()
    private val uniq = AtomicInteger()
  }

  @Test
  fun `a library class file resolves to its library descriptor, computed once`(): Unit = timeoutRunBlocking {
    val p = project.get()
    val tag = uniq.incrementAndGet()
    val module = createModule(p, "msolcr_lib_$tag")
    val (libRoot, libFile) = createLibraryRoot(p, "msolcr_lib_root_$tag")
    val library = createProjectLibrary(p, "msolcr_lib_$tag", libRoot)
    addLibraryDependency(module, library)

    readAction {
      val index = ProjectFileIndex.getInstance(p)
      val first = index.getModuleSourceOrLibraryClassesRoots(libFile).single()
      val descriptor = assertInstanceOf(LibraryRootDescriptor::class.java, first)
      assertEquals(libRoot, descriptor.root)
      assertEquals(library, descriptor.library)

      val second = index.getModuleSourceOrLibraryClassesRoots(libFile).single()
      assertSame(first, second) { "the descriptor of an unchanged root must be computed once, not per call" }
    }
  }

  @Test
  fun `a module source file resolves to its module descriptor, computed once`(): Unit = timeoutRunBlocking {
    val p = project.get()
    val tag = uniq.incrementAndGet()
    val module = createModule(p, "msolcr_src_$tag")
    val (srcRoot, srcFile) = addSourceRoot(module, "msolcr_src_root_$tag")

    readAction {
      val index = ProjectFileIndex.getInstance(p)
      val first = index.getModuleSourceOrLibraryClassesRoots(srcFile).single()
      val descriptor = assertInstanceOf(ModuleRootDescriptor::class.java, first)
      assertEquals(srcRoot, descriptor.root)
      assertSame(module, descriptor.module)

      val second = index.getModuleSourceOrLibraryClassesRoots(srcFile).single()
      assertSame(first, second) { "the descriptor of an unchanged root must be computed once, not per call" }
    }
  }

  @Test
  fun `replacing a library replaces the descriptor instead of serving a stale cached one`(): Unit = timeoutRunBlocking {
    val p = project.get()
    val tag = uniq.incrementAndGet()
    val module = createModule(p, "msolcr_replace_$tag")
    val (libRoot, libFile) = createLibraryRoot(p, "msolcr_replace_root_$tag")
    val oldLibrary = createProjectLibrary(p, "msolcr_replace_old_$tag", libRoot)
    addLibraryDependency(module, oldLibrary)

    // Prime whatever cache the index keeps for this root.
    readAction {
      val primed = ProjectFileIndex.getInstance(p).getModuleSourceOrLibraryClassesRoots(libFile).single()
      assertEquals(oldLibrary, assertInstanceOf(LibraryRootDescriptor::class.java, primed).library)
    }

    edtWriteAction {
      LibraryTablesRegistrar.getInstance().getLibraryTable(p).removeLibrary(oldLibrary)
    }
    val newLibrary = createProjectLibrary(p, "msolcr_replace_new_$tag", libRoot)
    addLibraryDependency(module, newLibrary)

    readAction {
      val descriptor = ProjectFileIndex.getInstance(p).getModuleSourceOrLibraryClassesRoots(libFile).single()
      assertEquals(newLibrary, assertInstanceOf(LibraryRootDescriptor::class.java, descriptor).library) {
        "the root's owner changed, so the old descriptor must not be served from a cache"
      }
    }
  }
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

private suspend fun addSourceRoot(module: Module, name: String): Pair<VirtualFile, VirtualFile> {
  val basePath = module.project.basePath ?: error("project basePath is null")
  val rootPath = Path(basePath).resolve(name)
  val rootVfs = edtWriteAction {
    VfsUtil.createDirectoryIfMissing(rootPath.toString())
    ?: error("Failed to create source root at $rootPath")
  }
  val sourceFile = edtWriteAction {
    rootVfs.createChildData(null, "A.java")
  }
  edtWriteAction {
    ModuleRootModificationUtil.updateModel(module) { model ->
      model.addContentEntry(rootVfs).addSourceFolder(rootVfs, false)
    }
  }
  return rootVfs to sourceFile
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

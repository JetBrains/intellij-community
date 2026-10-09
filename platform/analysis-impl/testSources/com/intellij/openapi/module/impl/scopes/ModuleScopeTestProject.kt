// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.InheritedSdkDependency
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSourceDependency
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer
import java.nio.file.Files

/**
 * The synthetic project models the scope-container tests run against, and the project setup that makes a run
 * measure the container it claims to measure.
 *
 * Shared by [ModuleScopeMemoryTest], which measures what a container costs, and [ModuleScopeLookupBenchmarkTest],
 * which measures what asking it a question costs. The shapes are calibrated against the roots-per-entry measured
 * on the real monorepo (see the table in [ModuleScopeMemoryTest]'s KDoc) and that calibration is the point of
 * reusing them: a lookup benchmark on an uncalibrated shape measures a project that does not exist.
 */

/**
 * A family of project models, parameterised by what actually drives the per-container cost: how many source roots
 * sit behind one module, and how wide the exported-dependency closure of a module is.
 */
internal class ProjectShape(
  val id: String,
  val moduleCount: Int,
  /** Source roots of module `index`. Encodes the skew; the real Bazel project ranges from 0 to 10677. */
  val sourceRootsAt: (Int) -> Int,
  /**
   * Test source roots of module `index`, on top of [sourceRootsAt]. Zero everywhere by default: the memory of a
   * container does not depend on the test-ness of its roots, but the cost of `contains` does - it is the one
   * branch of [EntrySetRootContainer.acceptsRoot] that consults [EntityRootOrder].
   */
  val testSourceRootsAt: (Int) -> Int = { 0 },
  /** Modules per layer; a module depends on the previous layer only, so `moduleCount / layerSize` bounds the depth. */
  val layerSize: Int = 1,
  /** Exported module dependencies, taken from the previous layer. 0 for a flat project. */
  val exportedDepsPerModule: Int = 0,
  /** Roots of the project-level library owned by each module, as Bazel gives every target its own library. */
  val ownLibraryRoots: Int = 0,
  /** Project-level libraries that every module depends on. */
  val sharedLibraries: Int = 0,
  val rootsPerSharedLibrary: Int = 0,
  /** Roots of the project SDK, inherited by every module. 0 to build a project without an SDK. */
  val sdkRootCount: Int = 0,
) {
  fun dependenciesOf(index: Int): IntRange {
    if (exportedDepsPerModule == 0) return IntRange.EMPTY
    val previousLayerStart = (index / layerSize - 1) * layerSize
    if (previousLayerStart < 0) return IntRange.EMPTY
    return previousLayerStart until (previousLayerStart + exportedDepsPerModule).coerceAtMost(index)
  }
}

/**
 * Opens a project built to [shape] with the scope cache pinned and the container mode forced, runs [action] on it,
 * and disposes everything.
 *
 * The registry flag is not optional and not replaceable by `MultiverseTestEnabler`: shared-source support is
 * decided once, when the project is created, and `intellij.platform.shared.source.support` is *true* in
 * `registry.properties`, so it is the classic mode that has to be asked for. A test that forgets this measures the
 * multiverse container in both of its arms - which is what `ModuleScopeMemoryTest` did until the container
 * assertion was added. Callers must still assert the container class; see [expectedContainerClass].
 */
internal fun <T> withScopeProject(shape: ProjectShape, multiverse: Boolean, action: (Project) -> T): T {
  val disposable = Disposer.newDisposable("${shape.id}-${if (multiverse) "multiverse" else "classic"}")
  try {
    // The cache weighs entries by root count and evicts above this limit; -1 (the current default, IJPL-252901)
    // means unbounded. Pin it so eviction cannot silently shrink what we measure.
    Registry.get("module.with.dependencies.scope.cache.max.total.entries.count").setValue(-1, disposable)
    Registry.get("intellij.platform.shared.source.support").setValue(multiverse, disposable)

    val projectDir = Files.createTempDirectory("module-scope-${shape.id}")
    val project = PlatformTestUtil.loadAndOpenProject(projectDir, disposable)
    val vfsRoot = generateProject(project, shape, disposable)
    try {
      return action(project)
    }
    finally {
      runWriteActionAndWait { VfsTestUtil.deleteFile(vfsRoot) }
    }
  }
  finally {
    Disposer.dispose(disposable)
  }
}

/** The container class a run in [multiverse] mode must have produced. */
internal fun expectedContainerClass(multiverse: Boolean): Class<out RootContainer> =
  if (multiverse) EntrySetRootContainer::class.java else ClassicRootContainer::class.java

/**
 * Creates the whole model in one write action, on the in-memory `temp:///` file system: a shape with thousands of
 * source roots is unaffordable through [com.intellij.testFramework.rules.ProjectModelRule], which commits a
 * separate write action and waits for indexes per module.
 */
internal fun generateProject(project: Project, shape: ProjectShape, disposable: Disposable): VirtualFile {
  val fsRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///")!!
  lateinit var shapeRoot: VirtualFile

  runWriteActionAndWait {
    shapeRoot = fsRoot.createChildDirectory(REQUESTOR, "${shape.id}-${System.nanoTime()}")
    val urlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    val builder = MutableEntityStorage.create()

    if (shape.sdkRootCount > 0) {
      val sdkRoot = shapeRoot.createChildDirectory(REQUESTOR, "sdk")
      val sdk = createSdk(shape, sdkRoot, disposable)
      ProjectRootManager.getInstance(project).projectSdk = sdk
    }

    val sharedLibraries = (0 until shape.sharedLibraries).map { libIndex ->
      val libRoot = shapeRoot.createChildDirectory(REQUESTOR, "shared-lib$libIndex")
      val roots = (0 until shape.rootsPerSharedLibrary).map { rootIndex ->
        LibraryRoot(urlManager.storeAndGet(libRoot.createChildDirectory(REQUESTOR, "root$rootIndex").url),
                    LibraryRootTypeId.COMPILED)
      }
      builder.addEntity(
        LibraryEntity("shared-lib$libIndex", LibraryTableId.ProjectLibraryTableId, roots, NonPersistentEntitySource)
      ).symbolicId
    }

    for (index in 0 until shape.moduleCount) {
      val moduleRoot = shapeRoot.createChildDirectory(REQUESTOR, "module$index")
      val dependencies = ArrayList<ModuleDependencyItem>()
      dependencies.add(ModuleSourceDependency)
      if (shape.sdkRootCount > 0) {
        dependencies.add(InheritedSdkDependency)
      }
      for (dep in shape.dependenciesOf(index)) {
        dependencies.add(ModuleDependency(ModuleId("module$dep"), exported = true, DependencyScope.COMPILE,
                                          productionOnTest = false))
      }
      if (shape.ownLibraryRoots > 0) {
        val libRoot = moduleRoot.createChildDirectory(REQUESTOR, "lib")
        val roots = (0 until shape.ownLibraryRoots).map { rootIndex ->
          LibraryRoot(urlManager.storeAndGet(libRoot.createChildDirectory(REQUESTOR, "root$rootIndex").url),
                      LibraryRootTypeId.COMPILED)
        }
        val own = builder.addEntity(
          LibraryEntity("lib$index", LibraryTableId.ProjectLibraryTableId, roots, NonPersistentEntitySource)
        )
        dependencies.add(LibraryDependency(own.symbolicId, exported = true, DependencyScope.COMPILE))
      }
      for (libraryId in sharedLibraries) {
        dependencies.add(LibraryDependency(libraryId, exported = true, DependencyScope.COMPILE))
      }

      val sourceRootCount = shape.sourceRootsAt(index)
      val testSourceRootCount = shape.testSourceRootsAt(index)
      builder.addEntity(ModuleEntity("module$index", dependencies, NonPersistentEntitySource) {
        contentRoots = listOf(
          ContentRootEntity(urlManager.storeAndGet(moduleRoot.url), emptyList(), NonPersistentEntitySource) {
            sourceRoots =
              (0 until sourceRootCount).map { rootIndex ->
                SourceRootEntity(urlManager.storeAndGet(moduleRoot.createChildDirectory(REQUESTOR, "src$rootIndex").url),
                                 JAVA_SOURCE, NonPersistentEntitySource)
              } +
              (0 until testSourceRootCount).map { rootIndex ->
                SourceRootEntity(urlManager.storeAndGet(moduleRoot.createChildDirectory(REQUESTOR, "test$rootIndex").url),
                                 JAVA_TEST_SOURCE, NonPersistentEntitySource)
              }
          }
        )
      })
    }

    WorkspaceModel.getInstance(project).updateProjectModel("set up ${shape.id}") {
      it.applyChangesFrom(builder)
    }
  }
  return shapeRoot
}

private fun createSdk(shape: ProjectShape, sdkRoot: VirtualFile, disposable: Disposable): Sdk {
  val jdkTable = ProjectJdkTable.getInstance()
  val sdk = jdkTable.createSdk("sdk-${shape.id}-${System.nanoTime()}", SimpleJavaSdkType.getInstance())
  val modificator = sdk.sdkModificator
  for (rootIndex in 0 until shape.sdkRootCount) {
    modificator.addRoot(sdkRoot.createChildDirectory(REQUESTOR, "root$rootIndex"), OrderRootType.CLASSES)
  }
  modificator.commitChanges()
  jdkTable.addJdk(sdk, disposable)
  return sdk
}

private val REQUESTOR = Any()

internal val JAVA_SOURCE: SourceRootTypeId = SourceRootTypeId(JpsModuleRootModelSerializer.JAVA_SOURCE_ROOT_TYPE_ID)

internal val JAVA_TEST_SOURCE: SourceRootTypeId = SourceRootTypeId(JpsModuleRootModelSerializer.JAVA_TEST_ROOT_TYPE_ID)

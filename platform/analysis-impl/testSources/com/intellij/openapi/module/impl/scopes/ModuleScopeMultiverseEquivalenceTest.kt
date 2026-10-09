// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.ModuleContext
import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkContext
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.LibraryContext
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
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
import com.intellij.psi.search.ActualContextFileInfo
import com.intellij.psi.search.DoesNotContainFileInfo
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * What [EntrySetRootContainer] has to get right, and what no byte measurement can check: that a scope which stores
 * entities rather than roots still answers every question the same way the root-storing one did.
 *
 * The test is differential - the same project model is built twice, once with shared-source support off so that
 * scopes use [ClassicRootContainer], once with it on so that they use [EntrySetRootContainer], and every answer of
 * `getRoots`, `contains` and `compare` is compared between the two. That covers the things the design notes list
 * as needing a test: the `TESTS` option filtering roots *within* a module source entry, a production-on-test
 * dependency inverting that filter, the order of a library's roots surviving, and the first-reachable entry
 * winning when a root is behind two of them. On top of that it checks the contexts, which have no classic
 * counterpart to compare against.
 *
 * See `docs/IntelliJ-Platform/4_man/Lang/Shared-Sources/Module-Scope-Root-Container.md`.
 */
@TestApplication
internal class ModuleScopeMultiverseEquivalenceTest {
  /**
   * Both halves live in one test method on purpose. [MultiverseTestEnabler] arms shared-source support for "the
   * next project" through application-global state, so a second test method that opens a multiverse project can
   * leave that state armed for whichever method JUnit runs next - which showed up here as the classic half of
   * this comparison silently getting an [EntrySetRootContainer]. The container assertions in [probeAll] are what
   * caught it, and they stay for that reason.
   */
  @Test
  fun `multiverse scopes answer what classic scopes answer`() {
    val classic = withProject(multiverse = false) { project -> probeAll(project, multiverse = false) }

    withProject(multiverse = true) { project ->
      val multiverse = probeAll(project, multiverse = true)

      // Guard against the comparison passing because it asked nothing: the shape has 5 modules, 5 masks and 23
      // probe files, so each mode answers 5 * 5 * (1 + 23 + 23 * 22 / 2) = 6925 questions.
      assertEquals(6925, classic.size, "the shape or the probe list changed; check the expectation above too")
      assertEquals(classic.keys, multiverse.keys, "the two modes must ask the same questions")
      val differences = classic.keys.filter { classic[it] != multiverse[it] }
      assertTrue(differences.isEmpty()) {
        "the two modes disagree on ${differences.size} of ${classic.size} questions:\n" +
        differences.take(20).joinToString("\n") { "  $it: classic=${classic[it]}, multiverse=${multiverse[it]}" }
      }

      assertContextsAreReported(project)
    }
  }

  /**
   * One root behind two different libraries of one module. The classic container stored one owner per root, so the
   * scope reported the first owner's context and no other. The entry-set container reaches both entities, and
   * reporting both gives the file two contexts: PSI then loads one copy of a class per context, and a resolve that
   * crosses the copies breaks every identity comparison (IJPL-255937, the Spring SecurityInlaysTest pair on TC).
   * The scope keeps the classic answer until IJPL-339 supports several owners of one root.
   */
  @Test
  fun `a root behind two libraries keeps one owner`() {
    withProject(multiverse = true, shape = ::generateTwoLibrariesOneRoot) { project ->
      val module = ModuleManager.getInstance(project).modules.single()
      val scope = module.getModuleRuntimeScope(false) as RootContainerScope
      val file = probeIn(project, "lib/root")
      assertTrue(scope.contains(file)) { "the probe under the shared library root is in the module scope" }

      val contexts = contextsOf(scope, file)
      val libraries = contexts.map { (it as LibraryContext).getLibrary()?.name }
      assertEquals(listOf("libX"), libraries) {
        "a root behind two libraries must keep its first owner only, got $contexts"
      }

      // contains(file, context) agrees: the file is reached through libX, and through nothing else
      assertTrue(scope.contains(file))
      assertTrue(scope.contains(file, contexts.single()))
      val libY = LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraryByName("libY")!!
      val libYContext = ProjectModelContextBridge.getInstance(project).getContext(libY)!!
      assertFalse(scope.contains(file, libYContext)) { "the file is not reached through the second owner" }
    }
  }

  /** The shape of [a root behind two libraries keeps one owner]: one module, two libraries, one shared root. */
  private fun generateTwoLibrariesOneRoot(project: Project, @Suppress("unused") disposable: Disposable): VirtualFile {
    val fsRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///")!!
    lateinit var shapeRoot: VirtualFile
    runWriteActionAndWait {
      shapeRoot = fsRoot.createChildDirectory(this, "dup-lib-root-${System.nanoTime()}")
      val urlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
      val builder = MutableEntityStorage.create()

      val root = shapeRoot.createChildDirectory(this, "lib").createChildDirectory(this, "root")
      VfsTestUtil.createFile(root, PROBE_FILE_NAME, "probe")
      val rootUrl = LibraryRoot(urlManager.storeAndGet(root.url), LibraryRootTypeId.COMPILED)

      fun library(name: String) = builder.addEntity(
        LibraryEntity(name, LibraryTableId.ProjectLibraryTableId, listOf(rootUrl), NonPersistentEntitySource)
      ).symbolicId

      val libX = library("libX")
      val libY = library("libY")
      val content = shapeRoot.createChildDirectory(this, "dupModule")
      val src = content.createChildDirectory(this, "src")
      VfsTestUtil.createFile(src, PROBE_FILE_NAME, "probe")
      builder.addEntity(ModuleEntity("dupModule", listOf(
        ModuleSourceDependency,
        LibraryDependency(libX, exported = false, DependencyScope.COMPILE),
        LibraryDependency(libY, exported = false, DependencyScope.COMPILE),
      ), NonPersistentEntitySource) {
        contentRoots = listOf(ContentRootEntity(urlManager.storeAndGet(content.url), emptyList(), NonPersistentEntitySource) {
          this.sourceRoots = listOf(SourceRootEntity(urlManager.storeAndGet(src.url), JAVA_SOURCE, NonPersistentEntitySource))
        })
      })

      WorkspaceModel.getInstance(project).updateProjectModel("set up the two-libraries-one-root shape") {
        it.applyChangesFrom(builder)
      }
    }
    return shapeRoot
  }

  /**
   * What has no classic counterpart at all: the code insight contexts a scope reports, which used to come from the
   * order entry stored next to a root and now come from the entity the file index names.
   */
  private fun assertContextsAreReported(project: Project) {
    val moduleB = ModuleManager.getInstance(project).modules.single { it.name == "moduleB" }
    val scope = moduleB.getModuleRuntimeScope(true) as RootContainerScope

    // a file under moduleB's own source root is reached through moduleB, and through nothing else
    val ownFile = probeIn(project, "moduleB/src")
    val ownContexts = contextsOf(scope, ownFile)
    assertEquals(listOf<Module?>(moduleB), ownContexts.map { (it as ModuleContext).getModule() })

    // a file under an SDK root is reached through the SDK
    val sdkFile = probeIn(project, "sdk/root0")
    val sdkContexts = contextsOf(scope, sdkFile)
    assertTrue(sdkContexts.isNotEmpty() && sdkContexts.all { it is SdkContext }) {
      "expected SDK contexts for $sdkFile, got $sdkContexts"
    }

    // contains(file, context) agrees with contains(file) for the owning context and rejects any other. The scope
    // reaches both moduleB and the SDK, so this only passes if the claimed context is checked against the root's
    // real owner rather than just looked up in the scope.
    assertTrue(scope.contains(ownFile))
    assertTrue(scope.contains(ownFile, ownContexts.single())) { "moduleB's own source root is in its own scope" }
    assertFalse(scope.contains(ownFile, sdkContexts.first())) {
      "a file under a module source root is not reached through the SDK"
    }

    // a file under no root at all is in no scope and has no context
    val outsideFile = probeIn(project, "moduleB/notASourceRoot")
    assertFalse(scope.contains(outsideFile))
    assertTrue(scope.getFileInfo(outsideFile) is DoesNotContainFileInfo)
  }

  private fun contextsOf(scope: RootContainerScope, file: VirtualFile): List<CodeInsightContext> {
    val info = scope.getFileInfo(file)
    assertTrue(info is ActualContextFileInfo) { "expected contexts for $file, got $info" }
    return (info as ActualContextFileInfo).contexts.toList()
  }

  /**
   * Every question the two modes can both be asked, keyed by a string that does not depend on the project
   * instance: `<module>/<mask>/<question>`.
   */
  private fun probeAll(project: Project, multiverse: Boolean): Map<String, String> {
    val answers = LinkedHashMap<String, String>()
    val modules = ModuleManager.getInstance(project).modules.sortedBy { it.name }
    assertTrue(modules.isNotEmpty(), "the shape produced no modules")
    val probes = probeFiles(project)

    for (module in modules) {
      for ((maskName, scopeOf) in MASKS) {
        val scope = scopeOf(module)
        val prefix = "${module.name}/$maskName"

        // Without this the whole comparison could pass by running the classic container twice.
        val container = (scope as RootContainerScope).rootContainer
        val expected = if (multiverse) EntrySetRootContainer::class.java else ClassicRootContainer::class.java
        assertEquals(expected, container.javaClass) { "$prefix is backed by the wrong container" }

        answers["$prefix/roots"] = module.moduleScopeOf(scope).joinToString(", ") { root ->
          relativePath(project, root)
        }
        for ((path, file) in probes) {
          answers["$prefix/contains($path)"] = scope.contains(file).toString()
        }
        // All ordered pairs, so that both the sign and its antisymmetry are compared.
        for ((path1, file1) in probes) {
          for ((path2, file2) in probes) {
            if (path1 >= path2) continue
            answers["$prefix/compare($path1,$path2)"] = scope.compare(file1, file2).coerceIn(-1, 1).toString()
          }
        }
      }
    }
    return answers
  }

  /** [ModuleWithDependenciesScope.roots] in rank order, which is the ordering `compare` exposes. */
  private fun Module.moduleScopeOf(scope: GlobalSearchScope): Collection<VirtualFile> =
    (scope as ModuleWithDependenciesScope).roots

  private fun probeFiles(project: Project): List<Pair<String, VirtualFile>> =
    PROBE_DIRS.map { dir -> dir to probeIn(project, dir) }

  private fun probeIn(project: Project, dir: String): VirtualFile {
    val root = shapeRootOf(project)
    return requireNotNull(root.findFileByRelativePath("$dir/$PROBE_FILE_NAME")) { "no probe file in $dir" }
  }

  private fun relativePath(project: Project, file: VirtualFile): String =
    file.path.removePrefix(shapeRootOf(project).path).removePrefix("/")

  private fun shapeRootOf(project: Project): VirtualFile =
    requireNotNull(project.getUserData(SHAPE_ROOT)) { "the shape root is set up by withProject" }

  private fun <T> withProject(
    multiverse: Boolean,
    shape: (Project, Disposable) -> VirtualFile = ::generate,
    action: (Project) -> T,
  ): T {
    val disposable = Disposer.newDisposable("scope-equivalence-${if (multiverse) "multiverse" else "classic"}")
    try {
      // Shared-source support is decided once, when the project is created, and cached in its user data - and it
      // is on by default (`intellij.platform.shared.source.support` is true in registry.properties), so it is the
      // classic mode that has to be asked for, not the multiverse one.
      Registry.get(SHARED_SOURCE_SUPPORT).setValue(multiverse, disposable)
      val projectDir = Files.createTempDirectory("scope-equivalence")
      val project = PlatformTestUtil.loadAndOpenProject(projectDir, disposable)
      val shapeRoot = shape(project, disposable)
      project.putUserData(SHAPE_ROOT, shapeRoot)
      try {
        return action(project)
      }
      finally {
        runWriteActionAndWait { VfsTestUtil.deleteFile(shapeRoot) }
      }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  /**
   * A model small enough to enumerate by hand and wide enough to hit every branch of the entry-set container:
   * production and test source roots in one module (the `TESTS` filter), a production-on-test dependency (the
   * filter inverted), a library with several roots reached from two modules (the within-entity order, and one
   * entity behind two order entries), and an SDK inherited by all of them.
   */
  private fun generate(project: Project, disposable: Disposable): VirtualFile {
    val fsRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///")!!
    lateinit var shapeRoot: VirtualFile

    runWriteActionAndWait {
      shapeRoot = fsRoot.createChildDirectory(this, "scope-equivalence-${System.nanoTime()}")
      val urlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
      val builder = MutableEntityStorage.create()

      fun dir(path: String): VirtualFile {
        var current = shapeRoot
        for (segment in path.split('/')) {
          current = current.findChild(segment) ?: current.createChildDirectory(this, segment)
        }
        VfsTestUtil.createFile(current, PROBE_FILE_NAME, "probe")
        return current
      }

      val sdkRoots = listOf(dir("sdk/root0"), dir("sdk/root1"))
      val sdk = createSdk(sdkRoots, disposable)
      ProjectRootManager.getInstance(project).projectSdk = sdk

      // Two roots, so that their order within the library is something compare can get wrong.
      val libraryRoots = listOf(dir("lib/root0"), dir("lib/root1"))
      val library = builder.addEntity(
        LibraryEntity("shared-lib", LibraryTableId.ProjectLibraryTableId,
                      libraryRoots.map { LibraryRoot(urlManager.storeAndGet(it.url), LibraryRootTypeId.COMPILED) },
                      NonPersistentEntitySource)
      ).symbolicId

      // Owned by moduleA and moduleE at once, which is what shared-source support is for: the file index reports
      // two owners for it, and a scope that reaches both has to agree with the classic container on whether the
      // file is in it and where it ranks.
      val sharedRoot = dir("shared/src")

      fun module(name: String, dependencies: List<ModuleDependencyItem>, withTests: Boolean,
                 sharesRoot: Boolean = false) {
        val content = dir(name)
        val sourceRoots = buildList {
          add(dir("$name/src") to JAVA_SOURCE)
          if (withTests) add(dir("$name/testSrc") to JAVA_TEST_SOURCE)
        }
        // a file in the content root but under no source root: in no scope, in either mode
        dir("$name/notASourceRoot")
        builder.addEntity(ModuleEntity(name, dependencies, NonPersistentEntitySource) {
          contentRoots = buildList {
            add(ContentRootEntity(urlManager.storeAndGet(content.url), emptyList(), NonPersistentEntitySource) {
              this.sourceRoots = sourceRoots.map { (file, type) ->
                SourceRootEntity(urlManager.storeAndGet(file.url), type, NonPersistentEntitySource)
              }
            })
            if (sharesRoot) {
              // its own content root, as a second owner of a shared source root has to declare it
              add(ContentRootEntity(urlManager.storeAndGet(sharedRoot.url), emptyList(), NonPersistentEntitySource) {
                this.sourceRoots = listOf(
                  SourceRootEntity(urlManager.storeAndGet(sharedRoot.url), JAVA_SOURCE, NonPersistentEntitySource))
              })
            }
          }
        })
      }

      module("moduleA", listOf(ModuleSourceDependency, InheritedSdkDependency,
                               LibraryDependency(library, exported = true, DependencyScope.COMPILE)),
             withTests = true, sharesRoot = true)
      module("moduleE", listOf(ModuleSourceDependency, InheritedSdkDependency),
             withTests = false, sharesRoot = true)
      // reaches both owners of the shared root, which is the case the union of two scopes also produces
      module("moduleB", listOf(ModuleSourceDependency, InheritedSdkDependency,
                               ModuleDependency(ModuleId("moduleA"), exported = true, DependencyScope.COMPILE,
                                                productionOnTest = false),
                               ModuleDependency(ModuleId("moduleE"), exported = true, DependencyScope.COMPILE,
                                                productionOnTest = false),
                               // the same library again, now behind a second order entry
                               LibraryDependency(library, exported = true, DependencyScope.COMPILE)),
             withTests = true)
      module("moduleC", listOf(ModuleSourceDependency, InheritedSdkDependency,
                               ModuleDependency(ModuleId("moduleB"), exported = true, DependencyScope.COMPILE,
                                                productionOnTest = true)),
             withTests = true)
      module("moduleD", listOf(ModuleSourceDependency, InheritedSdkDependency,
                               ModuleDependency(ModuleId("moduleA"), exported = false, DependencyScope.TEST,
                                                productionOnTest = false)),
             withTests = false)

      WorkspaceModel.getInstance(project).updateProjectModel("set up the equivalence shape") {
        it.applyChangesFrom(builder)
      }
    }
    return shapeRoot
  }

  private fun createSdk(roots: List<VirtualFile>, disposable: Disposable): Sdk {
    val jdkTable = ProjectJdkTable.getInstance()
    val sdk = jdkTable.createSdk("sdk-equivalence-${System.nanoTime()}", SimpleJavaSdkType.getInstance())
    val modificator = sdk.sdkModificator
    for (root in roots) {
      modificator.addRoot(root, OrderRootType.CLASSES)
    }
    modificator.commitChanges()
    jdkTable.addJdk(sdk, disposable)
    return sdk
  }
}

private const val SHARED_SOURCE_SUPPORT = "intellij.platform.shared.source.support"

private const val PROBE_FILE_NAME = "probe.txt"

/** One directory per root the model declares, plus content directories that are under no root at all. */
private val PROBE_DIRS: List<String> = listOf(
  "lib/root0", "lib/root1",
  "sdk/root0", "sdk/root1",
  "moduleA", "moduleA/src", "moduleA/testSrc", "moduleA/notASourceRoot",
  "moduleB", "moduleB/src", "moduleB/testSrc", "moduleB/notASourceRoot",
  "moduleC", "moduleC/src", "moduleC/testSrc", "moduleC/notASourceRoot",
  "moduleD", "moduleD/src", "moduleD/notASourceRoot",
  "moduleE", "moduleE/src", "moduleE/notASourceRoot",
  "shared/src",
)

private val SHAPE_ROOT = com.intellij.openapi.util.Key.create<VirtualFile>("scope.equivalence.shape.root")

/** The same masks [ModuleScopeMemoryTest] measures, reached through the same public API. */
private val MASKS: List<Pair<String, (Module) -> GlobalSearchScope>> = listOf(
  "productionSource" to { module: Module -> module.moduleProductionSourceScope },
  "moduleScope" to { module: Module -> module.moduleScope },
  "withDependencies" to { module: Module -> module.moduleWithDependenciesScope },
  "runtime" to { module: Module -> module.getModuleRuntimeScope(false) },
  "runtimeWithTests" to { module: Module -> module.getModuleRuntimeScope(true) },
)

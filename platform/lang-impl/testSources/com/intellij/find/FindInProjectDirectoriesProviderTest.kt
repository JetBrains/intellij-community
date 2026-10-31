// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find

import com.intellij.find.FindInProjectSearchEngine.Coverage
import com.intellij.find.FindInProjectSearchEngine.FindInProjectSearcher
import com.intellij.find.impl.FindInProjectDirectoriesProvider
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IndexingTestUtil.Companion.waitUntilIndexesAreReady
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors.CollectProcessor
import com.intellij.util.Processor
import com.intellij.util.indexing.testEntities.IndexingTestEntity2
import com.intellij.util.indexing.testEntities.NonIndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.Collections.synchronizedList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins the contract of [FindInProjectDirectoriesProvider]: a project search walks the directories of the provider
 * instead of the platform traversal, and they stand for the whole project (see `FindInProjectTask` and `ScopeWalker`).
 * The roots are non-indexable, so no index searcher finds their files, and the walk alone decides.
 */
@TestApplication
@RegistryKey("find.in.files.in.non.indexable.enable", "true")
class FindInProjectDirectoriesProviderTest {
  @RegisterExtension
  private val projectModel: ProjectModelExtension = ProjectModelExtension()
  private val baseDir get() = projectModel.baseProjectDir
  private val project get() = projectModel.project

  @TestDisposable
  private lateinit var disposable: Disposable

  private lateinit var firstRoot: VirtualFile
  private lateinit var secondRoot: VirtualFile

  @BeforeEach
  fun setup(): Unit = runBlocking {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonIndexableKindFileSetTestContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(ExternalNonIndexableKindFileSetTestContributor(), disposable)

    firstRoot = baseDir.newVirtualDirectory("first")
    secondRoot = baseDir.newVirtualDirectory("second")
    baseDir.newVirtualFile("first/first.txt", "first file with zq data".toByteArray())
    baseDir.newVirtualFile("second/second.txt", "second file with zq data".toByteArray())

    val urlManager = project.workspaceModel.getVirtualFileUrlManager()
    project.workspaceModel.update("add non-indexable roots") { storage ->
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(firstRoot.url), NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(secondRoot.url), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    waitUntilIndexesAreReady(project)
  }

  @Test
  fun `without a provider the project search takes the platform traversal`(): Unit = runBlocking {
    assertThat(findFileNames(projectModel())).containsExactlyInAnyOrder("first.txt", "second.txt")
  }

  @Test
  fun `the project search walks the directories of the provider`(): Unit = runBlocking {
    registerProvider(listOf(firstRoot))

    assertThat(findFileNames(projectModel())).containsExactlyInAnyOrder("first.txt")
  }

  @Test
  fun `a provider without a directory leaves the project search to the platform`(): Unit = runBlocking {
    registerProvider(emptyList())

    assertThat(findFileNames(projectModel())).containsExactlyInAnyOrder("first.txt", "second.txt")
  }

  @Test
  fun `a directory search does not ask the provider`(): Unit = runBlocking {
    val calls = registerProvider(listOf(firstRoot))

    val model = projectModel().apply {
      isProjectScope = false
      directoryName = secondRoot.path
    }
    assertThat(findFileNames(model)).containsExactlyInAnyOrder("second.txt")
    assertThat(calls.get()).isZero()
  }

  @Test
  fun `a module search does not ask the provider`(): Unit = runBlocking {
    val module = projectModel.createModule("a")
    val moduleRoot = baseDir.newVirtualDirectory("content")
    baseDir.newVirtualFile("content/module.txt", "module file with zq data".toByteArray())
    PsiTestUtil.addContentRoot(module, moduleRoot)
    val calls = registerProvider(listOf(moduleRoot, firstRoot))

    val model = projectModel().apply {
      isProjectScope = false
      moduleName = module.name
    }
    assertThat(findFileNames(model)).containsExactlyInAnyOrder("module.txt")
    assertThat(calls.get()).isZero()
  }

  @Test
  fun `a custom scope that searches in libraries reaches the library files`(): Unit = runBlocking {
    val module = projectModel.createModule("a")
    val libraryRoot = baseDir.newVirtualDirectory("lib")
    baseDir.newVirtualFile("lib/lib.txt", "library file with zq data".toByteArray())
    projectModel.addModuleLevelLibrary(module, "lib") { it.addRoot(libraryRoot.url, OrderRootType.CLASSES) }
    registerProvider(listOf(firstRoot))

    // a pattern without a trigram has no index candidates, so the walk alone decides
    val model = projectModel().apply {
      stringToFind = "zq"
      isProjectScope = false
      isCustomScope = true
      customScope = GlobalSearchScope.allScope(project)
    }
    assertThat(findFileNames(model)).containsExactlyInAnyOrder("first.txt", "lib.txt")
  }

  @Test
  fun `a custom scope that searches in libraries reaches the external non-indexable files`(): Unit = runBlocking {
    val externalRoot = baseDir.newVirtualDirectory("ext")
    baseDir.newVirtualFile("ext/ext.txt", "external file with zq data".toByteArray())
    val urlManager = project.workspaceModel.getVirtualFileUrlManager()
    project.workspaceModel.update("add an external non-indexable root") { storage ->
      storage.addEntity(IndexingTestEntity2(listOf(urlManager.storeAndGet(externalRoot.url)), emptyList(), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    waitUntilIndexesAreReady(project)
    registerProvider(listOf(firstRoot))

    val model = projectModel().apply {
      stringToFind = "zq"
      isProjectScope = false
      isCustomScope = true
      customScope = GlobalSearchScope.allScope(project)
    }
    assertThat(findFileNames(model)).containsExactlyInAnyOrder("first.txt", "ext.txt")
    // the external roots are libraries, so a project search leaves them out
    assertThat(findFileNames(projectModel().apply { stringToFind = "zq" })).containsExactlyInAnyOrder("first.txt")
  }

  @Test
  fun `an excluded root is walked and the excluded subdirectory of another root is skipped`(): Unit = runBlocking {
    val module = projectModel.createModule("a")
    val contentRoot = baseDir.newVirtualDirectory("content")
    val excludedDirectory = baseDir.newVirtualDirectory("content/excluded")
    val bannedRoot = baseDir.newVirtualDirectory("banned")
    baseDir.newVirtualFile("content/keep.txt", "kept file with zq data".toByteArray())
    baseDir.newVirtualFile("content/excluded/hidden.txt", "hidden file with zq data".toByteArray())
    baseDir.newVirtualFile("banned/banned.txt", "banned file with zq data".toByteArray())
    PsiTestUtil.addContentRoot(module, contentRoot)
    PsiTestUtil.addExcludedRoot(module, excludedDirectory)
    PsiTestUtil.addContentRoot(module, bannedRoot)
    PsiTestUtil.addExcludedRoot(module, bannedRoot)
    registerProvider(listOf(contentRoot, bannedRoot))

    assertThat(findFileNames(projectModel())).containsExactlyInAnyOrder("keep.txt", "banned.txt")
  }

  @Test
  fun `the directories of the provider stand for the project, so a searcher that covers all candidates skips their walk`(): Unit = runBlocking {
    registerProvider(listOf(firstRoot, secondRoot))
    val occurrence = checkNotNull(secondRoot.findChild("second.txt"))
    registerSearcherCoveringAllCandidates(listOf(occurrence))

    assertThat(findFileNames(projectModel())).containsExactlyInAnyOrder("second.txt")
  }

  /** Registers a provider that answers [directories], and gives the number of its calls. */
  private fun registerProvider(directories: List<VirtualFile>): AtomicInteger {
    val calls = AtomicInteger()
    val provider = object : FindInProjectDirectoriesProvider {
      override fun getProjectDirectories(project: Project): List<VirtualFile> {
        calls.incrementAndGet()
        return directories
      }
    }
    FindInProjectDirectoriesProvider.EP_NAME.point.registerExtension(provider, disposable)
    return calls
  }

  /** Registers a reliable searcher that feeds [occurrences] and reports [Coverage.ALL_CANDIDATES]. It covers no file. */
  private fun registerSearcherCoveringAllCandidates(occurrences: Collection<VirtualFile>) {
    val engine = FindInProjectSearchEngine { _, _ ->
      object : FindInProjectSearcher {
        override fun searchForOccurrences(): Collection<VirtualFile> = occurrences
        override fun processOccurrences(processor: Processor<in VirtualFile>): Coverage =
          if (super.processOccurrences(processor) == Coverage.STOPPED) Coverage.STOPPED else Coverage.ALL_CANDIDATES
        override fun isReliable(): Boolean = true
        override fun isCovered(file: VirtualFile): Boolean = false
      }
    }
    FindInProjectSearchEngine.EP_NAME.point.registerExtension(engine, disposable)
  }

  /** A search over the project for the word every test file holds. */
  private fun projectModel(): FindModel = FindModel().apply {
    stringToFind = "data"
    isGlobal = true
    isMultipleFiles = true
    isProjectScope = true
    isWithSubdirectories = true
  }

  private fun findFileNames(model: FindModel): List<String> {
    val usages = synchronizedList<UsageInfo?>(ArrayList())
    val consumer: Processor<UsageInfo?> = CollectProcessor(usages)
    val presentation = FindInProjectUtil.setupProcessPresentation(false, FindInProjectUtil.setupViewPresentation(false, model))
    FindInProjectUtil.findUsages(model, project, consumer, presentation)
    return usages.map { it!!.virtualFile!!.name }.distinct()
  }
}

/** Gives the roots of an [IndexingTestEntity2] the external non-indexable kind: a library root that no index covers. */
private class ExternalNonIndexableKindFileSetTestContributor : WorkspaceFileIndexContributor<IndexingTestEntity2> {
  override val entityClass: Class<IndexingTestEntity2> = IndexingTestEntity2::class.java

  override fun registerFileSets(entity: IndexingTestEntity2, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    for (root in entity.roots) {
      registrar.registerFileSet(root, WorkspaceFileKind.EXTERNAL_NON_INDEXABLE, entity, null)
    }
  }
}

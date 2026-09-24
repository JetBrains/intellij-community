// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.find.DirectorySearchEngine
import com.intellij.find.DirectorySearchEngine.FileSearchCandidate
import com.intellij.find.FindModel
import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.mock.MockProgressIndicator
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.utils.vfs.getPsiFile
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.indexing.testEntities.IndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.util.indexing.testEntities.NonIndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.util.indexing.testEntities.NonRecursiveFileSetContributor
import com.intellij.util.indexing.testEntities.NonRecursiveTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.toPath
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer


@TestApplication
@RegistryKey("se.enable.non.indexable.files.contributor", "true")
open class NonIndexableFilesSEContributorTest {
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @TestDisposable
  private lateinit var disposable: Disposable


  private val baseDir get() = projectModel.baseProjectDir
  private val project get() = projectModel.project
  private val workspaceModel get() = project.workspaceModel
  private val urlManager get() = workspaceModel.getVirtualFileUrlManager()


  @BeforeEach
  fun setUp() {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonIndexableKindFileSetTestContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(IndexableKindFileSetTestContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonRecursiveFileSetContributor(), disposable)
  }


  private fun searchNonIndexableFiles(pattern: String, scope: SearchScope? = null): Set<PsiFileSystemItem> {
    val contributor = NonIndexableFilesSEContributor(createEvent(project))
    contributor.setScope(ScopeDescriptor(scope))
    Disposer.register(disposable, contributor)

    val items = contributor.search(pattern, MockProgressIndicator().apply { start() }).toSet()

    assertThat(items).allSatisfy { it is PsiFileSystemItem }
    return items.filterIsInstance<PsiFileSystemItem>().toSet()
  }

  private fun registerNameSearchEngine(
    getWeight: (VirtualFile) -> Int = { if (it.isDirectory) 1 else -1 },
    searchNames: (VirtualFile, String, Consumer<FileSearchCandidate>) -> Unit,
  ) {
    val engine = TestDirectorySearchEngine(getWeight, searchNames)
    ExtensionTestUtil.maskExtensions(DirectorySearchEngine.EP_NAME, listOf(engine), disposable)
  }

  @Test
  fun `name search receives the path pattern`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val file = baseDir.newVirtualFile("root/sub/file.txt")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(root.url), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    var receivedPattern: String? = null
    registerNameSearchEngine { _, pathPattern, consumer ->
      receivedPattern = pathPattern
      consumer.accept(FileSearchCandidate.fromVirtualFile(file))
    }

    val names = searchNonIndexableFiles("sub\\fi*le").map { it.name }

    assertThat(receivedPattern).isEqualTo("sub/fi*le")
    assertThat(names).containsExactly("file.txt")
  }

  @Test
  fun `name search receives the pattern without a line suffix`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val file = baseDir.newVirtualFile("root/file.txt")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(root.url), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    var receivedPattern: String? = null
    registerNameSearchEngine { _, pathPattern, consumer ->
      receivedPattern = pathPattern
      consumer.accept(FileSearchCandidate.fromVirtualFile(file))
    }

    val names = searchNonIndexableFiles("file.txt:12").map { it.name }

    assertThat(receivedPattern).isEqualTo("file.txt")
    assertThat(names).containsExactly("file.txt")
  }

  @Test
  fun `name search accepts a path without a cached virtual file`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(root.url), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    val path = Files.createFile(baseDir.rootPath.resolve("root/path-file.txt"))
    registerNameSearchEngine { _, _, consumer -> consumer.accept(FileSearchCandidate.fromPath(path)) }

    val names = searchNonIndexableFiles("path-file").map { it.name }

    assertThat(names).containsExactly("path-file.txt")
  }

  @Test
  fun `name search returns the root and its descendants`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val file = baseDir.newVirtualFile("root/root-file.txt")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(root.url), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    var searchCalls = 0
    registerNameSearchEngine { directory, _, consumer ->
      searchCalls++
      consumer.accept(FileSearchCandidate.fromVirtualFile(directory))
      consumer.accept(FileSearchCandidate.fromVirtualFile(file))
    }

    val names = searchNonIndexableFiles("root").map { it.name }

    assertThat(names).containsExactlyInAnyOrder("root", "root-file.txt")
    assertThat(searchCalls).isEqualTo(1)
  }

  @Test
  fun `name search returns a nested non-indexable root with its qualified path`(): Unit = timeoutRunBlocking {
    val outer = baseDir.newVirtualDirectory("outer")
    val excluded = baseDir.newVirtualDirectory("outer/excluded")
    val nested = baseDir.newVirtualDirectory("outer/excluded/nested")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(outer.url), NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(nested.url), NonPersistentEntitySource))
      storage.addEntity(IndexingTestEntity(emptyList(), listOf(urlManager.storeAndGet(excluded.url)), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    registerNameSearchEngine { directory, _, consumer -> consumer.accept(FileSearchCandidate.fromVirtualFile(directory)) }

    val names = searchNonIndexableFiles("outer/excluded/nested").map { it.name }

    assertThat(names).containsExactly("nested")
  }

  @Test
  fun `an unavailable name search engine falls back to the local walk`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    baseDir.newVirtualFile("root/file.txt")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(root.url), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    registerNameSearchEngine(getWeight = { -1 }) { _, _, _ -> error("The engine is unavailable") }

    val names = searchNonIndexableFiles("file").map { it.name }

    assertThat(names).containsExactly("file.txt")
  }

  @Test
  fun `name search falls back to the local walk when its engine fails`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    baseDir.newVirtualFile("root/file.txt")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(root.url), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    val engineCalled = AtomicBoolean()
    registerNameSearchEngine { _, _, _ ->
      engineCalled.set(true)
      throw IOException("Connection refused")
    }

    val names = searchNonIndexableFiles("file").map { it.name }

    assertThat(engineCalled.get()).isTrue()
    assertThat(names).containsExactly("file.txt")
  }

  @Test
  fun `name search uses the engine for one root when another fails`(): Unit = timeoutRunBlocking {
    val failedRoot = baseDir.newVirtualDirectory("dead")
    val healthyRoot = baseDir.newVirtualDirectory("healthy")
    val healthyFile = baseDir.newVirtualFile("healthy/healthy-file.txt")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(failedRoot.url), NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(healthyRoot.url), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    val failedRootSearched = AtomicBoolean()
    val healthyRootSearched = AtomicBoolean()
    registerNameSearchEngine { directory, _, consumer ->
      if (directory.path == failedRoot.path) {
        failedRootSearched.set(true)
        throw IOException("Connection refused")
      }
      if (directory.path == healthyRoot.path) {
        healthyRootSearched.set(true)
        consumer.accept(FileSearchCandidate.fromVirtualFile(healthyFile))
      }
    }

    val names = searchNonIndexableFiles("file").map { it.name }

    assertThat(failedRootSearched.get()).isTrue()
    assertThat(healthyRootSearched.get()).isTrue()
    assertThat(names).containsExactly("healthy-file.txt")
  }

  @Test
  fun `consumer rejection stops name search result delivery`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val first = baseDir.newVirtualFile("root/first-file.txt")
    val second = baseDir.newVirtualFile("root/second-file.txt")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(root.url), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    registerNameSearchEngine { _, _, consumer ->
      consumer.accept(FileSearchCandidate.fromVirtualFile(first))
      consumer.accept(FileSearchCandidate.fromVirtualFile(second))
    }
    val contributor = NonIndexableFilesSEContributor(createEvent(project))
    Disposer.register(disposable, contributor)
    var consumerCalls = 0

    contributor.fetchWeightedElements("file", MockProgressIndicator().apply { start() }) {
      consumerCalls++
      false
    }

    assertThat(consumerCalls).isEqualTo(1)
  }

  @Test
  @Disabled("DirectorySearchEngine name search does not receive excluded subtrees")
  fun `name search excludes indexable subtrees`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val excluded = baseDir.newVirtualDirectory("root/excluded")
    val hidden = baseDir.newVirtualFile("root/excluded/hidden-file.txt")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(root.url), NonPersistentEntitySource))
      storage.addEntity(IndexingTestEntity(emptyList(), listOf(urlManager.storeAndGet(excluded.url)), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    registerNameSearchEngine { _, _, consumer -> consumer.accept(FileSearchCandidate.fromVirtualFile(hidden)) }

    assertThat(searchNonIndexableFiles("hidden-file")).isEmpty()
  }

  @Test
  fun `consumer rejection stops optimal and suboptimal result delivery`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("a")
    baseDir.newVirtualFile("a/b/c/abc-first.txt")
    baseDir.newVirtualFile("a/b/c/abc-second.txt")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(urlManager.storeAndGet(root.url), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    assertThat(searchNonIndexableFiles("abc").map { it.name })
      .containsExactlyInAnyOrder("abc-first.txt", "abc-second.txt", "c")

    val contributor = NonIndexableFilesSEContributor(createEvent(project))
    Disposer.register(disposable, contributor)
    val consumedNames = mutableListOf<String>()

    contributor.fetchWeightedElements("abc", MockProgressIndicator().apply { start() }) {
      consumedNames.add((it.item as PsiFileSystemItem).name)
      false
    }

    assertThat(consumedNames).singleElement().isIn("abc-first.txt", "abc-second.txt")
  }

  @Test
  fun `unindexed under exclude`(): Unit = runBlocking {
    val unindexed = baseDir.newVirtualDirectory("u1").toVirtualFileUrl(urlManager)
    val exclude = baseDir.newVirtualDirectory("u1/exclude").toVirtualFileUrl(urlManager)
    val unindexed2 = baseDir.newVirtualDirectory("u1/exclude/u2").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("u1/exclude/u2/f")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed, NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(unindexed2, NonPersistentEntitySource))
      storage.addEntity(IndexingTestEntity(emptyList(), listOf(exclude), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val items = searchNonIndexableFiles("u")
    val names = items.map { it.name }
    assertThat(names).containsExactlyInAnyOrder("u1", "u2")
  }

  @Test
  fun `2 non-indexable roots on one directory`(): Unit = runBlocking {
    val unindexed = baseDir.newVirtualDirectory("u1").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("u1/d1/f1")
    baseDir.newVirtualFile("u1/d1/f2")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed, NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(unindexed, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val items = searchNonIndexableFiles("f")
    val names = items.map { it.name }
    assertThat(names).containsExactlyInAnyOrder("f1", "f2")
  }

  @Test
  fun `search scope includes only one file of two`(): Unit = runBlocking {
    val unindexed1 = baseDir.newVirtualDirectory("u1").toVirtualFileUrl(urlManager)
    val unindexed2 = baseDir.newVirtualDirectory("u2").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("u1/d1/f1")
    val f2 = baseDir.newVirtualFile("u2/d2/f2")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed1, NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(unindexed2, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val scope = readAction {
      val f2PsiFile = f2.getPsiFile(project)
      GlobalSearchScope.projectScope(project).intersectWith(GlobalSearchScope.fileScope(f2PsiFile))
    }
    val items = searchNonIndexableFiles("f", scope)
    val names = items.map { it.name }
    assertThat(names).containsExactlyInAnyOrder("f2")
  }

  @Test
  fun `unindexed under content`(): Unit = runBlocking {
    val unindexed = baseDir.newVirtualDirectory("u1").toVirtualFileUrl(urlManager)
    val content = baseDir.newVirtualDirectory("u1/content").toVirtualFileUrl(urlManager)
    val unindexed2 = baseDir.newVirtualDirectory("u1/content/u2").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("u1/content/u2/f")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed, NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(unindexed2, NonPersistentEntitySource))
      storage.addEntity(IndexingTestEntity(listOf(content), emptyList(), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val items = searchNonIndexableFiles("u")
    val names = items.map { it.name }
    assertThat(names).containsExactlyInAnyOrder("u1") // `u2` is excluded because it's under content root
  }

  @Test
  fun `unindexed under unindexed`(): Unit = runBlocking {
    val unindexed = baseDir.newVirtualDirectory("u1").toVirtualFileUrl(urlManager)
    baseDir.newVirtualDirectory("u1/justDir").toVirtualFileUrl(urlManager)
    val unindexed2 = baseDir.newVirtualDirectory("u1/justDir/u2").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("u1/justDir/u2/f")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed, NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(unindexed2, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val items = searchNonIndexableFiles("u")
    val names = items.map { it.name }
    assertThat(names).containsExactlyInAnyOrder("u1", "u2", "justDir")
  }

  @Test
  fun `indexable non-recursive file set inside non-indexable`(): Unit = runBlocking {
    val nonIndexable = baseDir.newVirtualDirectory("non-indexable").toVirtualFileUrl(urlManager)
    val indexableNonRecursive = baseDir.newVirtualDirectory("non-indexable/indexable-non-recursive").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("non-indexable/indexable-non-recursive/non-indexable-file.txt")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(nonIndexable, NonPersistentEntitySource))
      storage.addEntity(NonRecursiveTestEntity(indexableNonRecursive, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val items = searchNonIndexableFiles("non-indexable")
    val names = items.map { it.name }
    assertThat(names).containsExactlyInAnyOrder("non-indexable-file.txt", "non-indexable")
  }

  @Test
  fun `unindexed and non-recursive file set at the same level`(): Unit = runBlocking {
    val root = baseDir.newVirtualDirectory("root-file").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("root-file/file.txt")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(root, NonPersistentEntitySource))
      storage.addEntity(NonRecursiveTestEntity(root, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val items = searchNonIndexableFiles("file")
    val names = items.map { it.name }
    assertThat(names).containsExactlyInAnyOrder("file.txt") // `root` is excluded because it's under non-recursive content root
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `symlink to file`(): Unit = runBlocking {
    val unindexed = baseDir.newVirtualDirectory("u1").toVirtualFileUrl(urlManager)
    val f = baseDir.newVirtualFile("u1/d2/f3-1").toNioPath()
    Files.createSymbolicLink(baseDir.rootPath.resolve("u1/d2/f3-2"), f)
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val items = searchNonIndexableFiles("f")
    val names = items.map { it.name }
    assertThat(names).contains("f3-1", "f3-2")
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `circular symlinks`(): Unit = runBlocking {
    val unindexed1 = baseDir.newVirtualDirectory("u1").toVirtualFileUrl(urlManager)
    val unindexed2 = baseDir.newVirtualDirectory("u2").toVirtualFileUrl(urlManager)
    Files.createSymbolicLink(baseDir.rootPath.resolve("u1/u-link-1"), unindexed2.toPath())
    Files.createSymbolicLink(baseDir.rootPath.resolve("u2/u-link-2"), unindexed1.toPath())

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed1, NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(unindexed2, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val items = searchNonIndexableFiles("u")
    val names = items.map { it.name }
    assertThat(names).containsExactlyInAnyOrder("u1", "u2", "u-link-1", "u-link-1", "u-link-2", "u-link-2")
  }

  @Test
  fun `default search everywhere doesn't work`(): Unit = runBlocking {
    val unindexed1 = baseDir.newVirtualDirectory("dir1").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("dir1/file1")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed1, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val contributor = FileSearchEverywhereContributor(createEvent(project))
    Disposer.register(disposable, contributor)

    val items = contributor.search("file1", MockProgressIndicator().apply { start() })
    assertThat(items).isEmpty()
  }

  @Test
  fun `search everywhere`(): Unit = runBlocking {
    val unindexed1 = baseDir.newVirtualDirectory("dir1").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("dir1/file1")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed1, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val contributor = NonIndexableFilesSEContributor(createEvent(project))
    Disposer.register(disposable, contributor)

    val items = contributor.search("file1", MockProgressIndicator().apply { start() }).toSet()

    assertThat(items).allSatisfy { it is PsiFileSystemItem }
    val names = items.map { (it as PsiFileSystemItem).name }
    assertThat(names).containsExactlyInAnyOrder("file1")
  }


  @Test
  fun `file is not found because it has hidden type`(): Unit = runBlocking {
    val unindexed1 = baseDir.newVirtualDirectory("dir1").toVirtualFileUrl(urlManager)
    val file1 = baseDir.newVirtualFile("dir1/file1")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed1, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val contributor = NonIndexableFilesSEContributor(createEvent(project))
    contributor.setHiddenTypes(listOf(FileTypeRef.forFileType(file1.fileType)))
    Disposer.register(disposable, contributor)

    val items = contributor.search("file1", MockProgressIndicator().apply { start() }).toSet()

    assertThat(items).isEmpty()
  }

  @Test
  fun `search everywhere with slashes`(): Unit = runBlocking {
    val unindexed1 = baseDir.newVirtualDirectory("dir1").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("dir1/file1")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed1, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val items = searchNonIndexableFiles("dir1/file1")
    val names = items.map { it.name }
    assertThat(names).containsExactlyInAnyOrder("file1")
  }

  @Test
  fun `search everywhere with slashes, inner dir in pattern`(): Unit = runBlocking {
    val unindexed1 = baseDir.newVirtualDirectory("dir1").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("dir1/folder/file1")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed1, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()


    val items = searchNonIndexableFiles("folder/file1")
    val names = items.map { it.name }
    assertThat(names).containsExactlyInAnyOrder("file1")
  }
}

private class TestDirectorySearchEngine(
  private val weight: (VirtualFile) -> Int,
  private val nameSearch: (VirtualFile, String, Consumer<FileSearchCandidate>) -> Unit,
) : DirectorySearchEngine {
  override fun canSearch(findModel: FindModel): Boolean = false

  override fun canSearchNames(): Boolean = true

  override fun getWeight(directory: VirtualFile): Int = weight(directory)

  override fun searchDirectory(
    directory: VirtualFile,
    findModel: FindModel,
    consumer: Consumer<in Collection<VirtualFile>>,
  ) = error("Content search is not supported")

  override fun searchNames(directory: VirtualFile, pathPattern: String, consumer: Consumer<FileSearchCandidate>) {
    nameSearch(directory, pathPattern, consumer)
  }
}


private fun createEvent(project: Project): AnActionEvent {
  val projectContext = SimpleDataContext.getProjectContext(project)
  return TestActionEvent.createTestEvent(projectContext)
}

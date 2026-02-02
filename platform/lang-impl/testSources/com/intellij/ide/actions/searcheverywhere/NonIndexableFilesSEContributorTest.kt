// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.mock.MockProgressIndicator
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.VfsTestUtil
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Files


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

    val items = contributor.search(pattern, MockProgressIndicator()).toSet()

    assertThat(items).allSatisfy { it is PsiFileSystemItem }
    return items.filterIsInstance<PsiFileSystemItem>().toSet()
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

    val items = contributor.search("file1", MockProgressIndicator())
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

    val items = contributor.search("file1", MockProgressIndicator()).toSet()

    assertThat(items).allSatisfy { it is PsiFileSystemItem }
    val names = items.map { (it as PsiFileSystemItem).name }
    assertThat(names).containsExactlyInAnyOrder("file1")
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


private fun createEvent(project: Project): AnActionEvent {
  val projectContext = SimpleDataContext.getProjectContext(project)
  return TestActionEvent.createTestEvent(projectContext)
}


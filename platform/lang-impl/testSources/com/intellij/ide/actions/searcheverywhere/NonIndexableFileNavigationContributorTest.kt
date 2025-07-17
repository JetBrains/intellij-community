// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.util.gotoByName.NonIndexableFileNavigationContributor
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
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.CommonProcessors
import com.intellij.util.indexing.testEntities.*
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
@RegistryKey("search.in.non.indexable", "true")
class NonIndexableFileNavigationContributorTest {
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


  private suspend fun processNames(): Collection<String?> {
    val contributor = NonIndexableFileNavigationContributor()
    return readAction {
      val processor = CommonProcessors.CollectProcessor<String>()
      contributor.processNames(processor, GlobalSearchScope.projectScope(project), null)
      processor.results
    }
  }

  @Test
  fun `finds nothing if no CONTENT_UNINDEXABLE filesets`(): Unit = runBlocking {
    baseDir.newVirtualFile("a/b/c/d")

    val names = processNames()
    assertThat(names).isEmpty()
  }

  @Test
  fun `2 files 1 fileset`(): Unit = runBlocking {
    val unindexed = baseDir.newVirtualDirectory("u1").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("u1/d2/f3-1")
    baseDir.newVirtualFile("u1/d2/f3-2")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed, NonPersistentEntitySource))
    }

    val names = processNames()
    assertThat(names).containsExactlyInAnyOrder("u1", "d2", "f3-1", "f3-2")
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

    val names = processNames()
    assertThat(names).containsExactlyInAnyOrder("u1", "u2", "f")
  }

  @Test
  fun `2 non-indexable roots on one directory`(): Unit = runBlocking {
    val unindexed = baseDir.newVirtualDirectory("u1").toVirtualFileUrl(urlManager)
    val unindexed2 = unindexed
    baseDir.newVirtualFile("u1/d1/f1")
    baseDir.newVirtualFile("u1/d1/f2")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed, NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(unindexed2, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val names = processNames()
    assertThat(names).containsExactlyInAnyOrder("u1", "d1", "f1", "f2")
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

    val names = processNames()
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

    val names = processNames()
    assertThat(names).containsExactlyInAnyOrder("u1", "u2", "justDir", "f")
  }

  @Test
  fun `indexable non-recursive file set inside non-indexable`(): Unit = runBlocking {
    val nonIndexable = baseDir.newVirtualDirectory("non-indexable").toVirtualFileUrl(urlManager)
    val indexableNonRecursive = baseDir.newVirtualDirectory("non-indexable/indexable-non-recursive").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("non-indexable/indexable-non-recursive/file.txt")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(nonIndexable, NonPersistentEntitySource))
      storage.addEntity(NonRecursiveTestEntity(indexableNonRecursive, NonPersistentEntitySource))
    }

    val names = processNames()
    assertThat(names).containsExactlyInAnyOrder("file.txt", "non-indexable")
  }

  @Test
  fun `unindexed and non-recursive file set at the same level`(): Unit = runBlocking {
    val root = baseDir.newVirtualDirectory("root").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("root/file.txt")

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(root, NonPersistentEntitySource))
      storage.addEntity(NonRecursiveTestEntity(root, NonPersistentEntitySource))
    }

    val names = processNames()
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

    val names = processNames()
    assertThat(names).contains("f3-1", "f3-2")
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `circular symlinks`(): Unit = runBlocking {
    val unindexed1 = baseDir.newVirtualDirectory("u1").toVirtualFileUrl(urlManager)
    val unindexed2 = baseDir.newVirtualDirectory("u2").toVirtualFileUrl(urlManager)
    Files.createSymbolicLink(baseDir.rootPath.resolve("u1/link-1"), unindexed2.toPath())
    Files.createSymbolicLink(baseDir.rootPath.resolve("u2/link-2"), unindexed1.toPath())

    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(unindexed1, NonPersistentEntitySource))
      storage.addEntity(NonIndexableTestEntity(unindexed2, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val names = processNames()
    assertThat(names).containsExactlyInAnyOrder("u1", "u2", "link-1", "link-2")
  }

  @Test
  @RegistryKey("search.in.non.indexable", "false")
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

    val contributor = FileSearchEverywhereContributor(createEvent(project))
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

    val contributor = FileSearchEverywhereContributor(createEvent(project))
    Disposer.register(disposable, contributor)

    val items = contributor.search("dir1/file1", MockProgressIndicator()).toSet()

    assertThat(items).allSatisfy { it is PsiFileSystemItem }
    val names = items.map { (it as PsiFileSystemItem).name }
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

    val contributor = FileSearchEverywhereContributor(createEvent(project))
    Disposer.register(disposable, contributor)

    val items = contributor.search("folder/file1", MockProgressIndicator()).toSet()

    assertThat(items).allSatisfy { it is PsiFileSystemItem }
    val names = items.map { (it as PsiFileSystemItem).name }
    assertThat(names).containsExactlyInAnyOrder("file1")
  }
}


private fun createEvent(project: Project): AnActionEvent {
  val projectContext = SimpleDataContext.getProjectContext(project)
  return TestActionEvent.createTestEvent(projectContext)
}


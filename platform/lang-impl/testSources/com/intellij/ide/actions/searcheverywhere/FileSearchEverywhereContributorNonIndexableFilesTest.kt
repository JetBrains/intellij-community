// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.mock.MockProgressIndicator
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.indexing.testEntities.NonIndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * The index-based [FileSearchEverywhereContributor] does not find a file under a non-indexable root.
 */
@TestApplication
class FileSearchEverywhereContributorNonIndexableFilesTest {
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `the index-based contributor does not find a non-indexable file`(): Unit = runBlocking {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonIndexableKindFileSetTestContributor(), disposable)
    val project = projectModel.project
    val urlManager = project.workspaceModel.getVirtualFileUrlManager()
    val nonIndexableRoot = projectModel.baseProjectDir.newVirtualDirectory("dir1").toVirtualFileUrl(urlManager)
    projectModel.baseProjectDir.newVirtualFile("dir1/file1")
    project.workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(nonIndexableRoot, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()

    val contributor = FileSearchEverywhereContributor(TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project)))
    Disposer.register(disposable, contributor)

    val items = contributor.search("file1", MockProgressIndicator().apply { start() })
    assertThat(items).isEmpty()
  }
}

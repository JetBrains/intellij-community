// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find

import com.intellij.find.impl.FindInProjectUtil
import com.intellij.find.impl.FindInProjectUtil.FIND_IN_FILES_SEARCH_IN_NON_INDEXABLE
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.testFramework.IndexingTestUtil.Companion.waitUntilIndexesAreReady
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor
import com.intellij.util.indexing.FilesDeque
import com.intellij.util.indexing.testEntities.IndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.util.indexing.testEntities.NonIndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.util.indexing.testEntities.NonRecursiveFileSetContributor
import com.intellij.util.indexing.testEntities.NonRecursiveTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.*

/**
 * Run with `-cp intellij.idea.ultimate.test.main`
 */
@TestApplication
class SearchInNonIndexableTest() {
  @RegisterExtension
  private val projectModel: ProjectModelExtension = ProjectModelExtension()
  private val baseDir get() = projectModel.baseProjectDir
  private val project get() = projectModel.project
  private val workspaceModel get() = project.workspaceModel
  private val urlManager get() = workspaceModel.getVirtualFileUrlManager()

  @TestDisposable
  private lateinit var disposable: Disposable


  @BeforeEach
  fun setup(): Unit = runBlocking {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonIndexableKindFileSetTestContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(IndexableKindFileSetTestContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonRecursiveFileSetContributor(), disposable)

    project.putUserData(FIND_IN_FILES_SEARCH_IN_NON_INDEXABLE, true)

    val nonIndexable = baseDir.newVirtualDirectory("non-indexable").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("non-indexable/file1", "this is a file with some data".toByteArray())

    baseDir.newVirtualFile("non-indexable/file2", "this is a file with some <DELETED>".toByteArray())

    val indexable = baseDir.newVirtualDirectory("indexable").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("indexable/infile1", "this is a file with some data and indexes".toByteArray())

    val indexableNonRecursive = baseDir.newVirtualDirectory("non-indexable/indexable-non-recursive").toVirtualFileUrl(urlManager)
    baseDir.newVirtualFile("non-indexable/indexable-non-recursive/non-indexable-beats-non-recursive-content", "this is a file with some data".toByteArray())

    project.workspaceModel.update("add non-indexable root") { storage ->
      storage.addEntity(NonIndexableTestEntity(nonIndexable, NonPersistentEntitySource))
      storage.addEntity(IndexingTestEntity(listOf(indexable), emptyList(), NonPersistentEntitySource))
      storage.addEntity(NonRecursiveTestEntity(indexableNonRecursive, NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    waitUntilIndexesAreReady(project)
  }

  @Test
  fun `non-indexable files deque`(): Unit = runBlocking {
    val deque = readAction { FilesDeque.nonIndexableDequeue((project))}
    val files = mutableListOf<VirtualFile>()
    while (true) {
      val file = readAction { deque.computeNext() } ?: break
      files.add(file)
    }
    val names = files.map { it.name }
    assertThat(names).containsExactlyInAnyOrder("non-indexable", "file1", "file2", "non-indexable-beats-non-recursive-content")
  }

  @Test
  fun `find 'data' in files`(): Unit = runBlocking {
    val model = model(stringToFind = "data")

    val usages = Collections.synchronizedList<UsageInfo?>(ArrayList())
    val consumer: Processor<UsageInfo?> = CommonProcessors.CollectProcessor<UsageInfo?>(usages)
    val presentation = FindInProjectUtil.setupProcessPresentation(false, FindInProjectUtil.setupViewPresentation(false, model))

    FindInProjectUtil.findUsages(model, project, consumer, presentation)
    val fileNames = usages.map { it!!.virtualFile!!.name }
    assertThat(fileNames).containsExactlyInAnyOrder("file1", "infile1", "non-indexable-beats-non-recursive-content")
  }


  @Test
  fun `find 'data' only in indexable`(): Unit = runBlocking {
    project.putUserData(FIND_IN_FILES_SEARCH_IN_NON_INDEXABLE, false)
    val model = model(stringToFind = "data")

    val usages = Collections.synchronizedList<UsageInfo?>(ArrayList())
    val consumer: Processor<UsageInfo?> = CommonProcessors.CollectProcessor<UsageInfo?>(usages)
    val presentation = FindInProjectUtil.setupProcessPresentation(false, FindInProjectUtil.setupViewPresentation(false, model))

    FindInProjectUtil.findUsages(model, project, consumer, presentation)
    val fileNames = usages.map { it!!.virtualFile!!.name }
    assertThat(fileNames).containsExactlyInAnyOrder("infile1")
  }

  @Test
  fun `find 'indexes' only in indexable`(): Unit = runBlocking {
    val model = model(stringToFind = "indexes")

    val usages = Collections.synchronizedList<UsageInfo?>(ArrayList())
    val consumer: Processor<UsageInfo?> = CommonProcessors.CollectProcessor<UsageInfo?>(usages)
    val presentation = FindInProjectUtil.setupProcessPresentation(false, FindInProjectUtil.setupViewPresentation(false, model))

    FindInProjectUtil.findUsages(model, project, consumer, presentation)
    val fileNames = usages.map { it!!.virtualFile!!.name }
    assertThat(fileNames).containsExactlyInAnyOrder("infile1")
  }


  private fun model(stringToFind: String): FindModel = FindModel().apply {
    this.stringToFind = stringToFind
    stringToReplace = ""
    isReplaceState = false
    isWholeWordsOnly = false
    searchContext = FindModel.SearchContext.ANY
    isFromCursor = false
    isForward = true
    isGlobal = true
    isRegularExpressions = false
    regExpFlags = 0
    isCaseSensitive = false
    isMultipleFiles = true
    isPromptOnReplace = true
    isReplaceAll = false
    isProjectScope = true
    directoryName = null
    isWithSubdirectories = true
    isSearchInProjectFiles = false
    fileFilter = null
    moduleName = null
    customScopeName = null
  }
}

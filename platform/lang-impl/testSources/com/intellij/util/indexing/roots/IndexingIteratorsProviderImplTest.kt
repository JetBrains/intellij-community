// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by a license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.containers.TreeNodeProcessingResult
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleRelatedRootData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
internal class IndexingIteratorsProviderImplTest {
  @RegisterExtension
  private val projectModel = ProjectModelExtension()

  private val project: Project get() = projectModel.project
  private val workspaceModel get() = project.workspaceModel
  private val disposable get() = projectModel.disposableRule.disposable


  @Test
  fun `iterates indexable content below non-indexable content`() = timeoutRunBlocking {
    val project = projectModel.project
    val module = projectModel.createModule()
    val nonIndexableRoot = projectModel.baseProjectDir.newVirtualDirectory("non-indexable")
    val nonIndexableFile = projectModel.baseProjectDir.newVirtualFile("non-indexable/non-indexable.txt")
    val indexableRoot = projectModel.baseProjectDir.newVirtualDirectory("non-indexable/indexable")
    val indexableFile = projectModel.baseProjectDir.newVirtualFile("non-indexable/indexable/indexable.txt")

    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(ModuleNonIndexableFileSetContributor(module), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(ModuleContentFileSetContributor(module), disposable)

    workspaceModel.update {
      it.addEntity(NonIndexableTestEntity(nonIndexableRoot.toVirtualFileUrl(), NonPersistentEntitySource))
      it.addEntity(IndexingTestEntity(listOf(indexableRoot.toVirtualFileUrl()), emptyList(), NonPersistentEntitySource))
    }

    val indexableFiles = readAction {
      val files = mutableSetOf<VirtualFile>()
      val processed = WorkspaceFileIndexEx.getInstance(project).processIndexableContentUnderDirectory(
        nonIndexableRoot,
        { file -> files.add(file); TreeNodeProcessingResult.CONTINUE },
        VirtualFileFilter.ALL,
      ) { true }
      assertTrue(processed)
      assertTrue(nonIndexableFile !in files)
      assertTrue(indexableRoot in files)
      assertTrue(indexableFile in files)
      files
    }

    val iteratedFiles = readAction {
      val iterators = IndexingIteratorsProviderImpl(project).getIndexingIterators()
      val files = mutableSetOf<VirtualFile>()
      for (iterator in iterators) {
        assertTrue(iterator.iterateFiles(project, ContentIterator { file -> files.add(file); true }, VirtualFileFilter.ALL))
      }
      files
    }

    assertTrue(iteratedFiles.containsAll(indexableFiles), "Missing indexable files: ${indexableFiles - iteratedFiles}")
  }

  private fun VirtualFile.toVirtualFileUrl(): VirtualFileUrl = toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())

  private class ModuleNonIndexableFileSetContributor(private val module: Module) :
    WorkspaceFileIndexContributor<NonIndexableTestEntity> {
    override val entityClass: Class<NonIndexableTestEntity> = NonIndexableTestEntity::class.java

    override fun registerFileSets(
      entity: NonIndexableTestEntity,
      registrar: WorkspaceFileSetRegistrar,
      storage: EntityStorage,
    ) {
      registrar.registerFileSet(entity.root, WorkspaceFileKind.CONTENT_NON_INDEXABLE, entity, ModuleRootData(module))
    }
  }

  private class ModuleContentFileSetContributor(private val module: Module) :
    WorkspaceFileIndexContributor<IndexingTestEntity> {
    override val entityClass: Class<IndexingTestEntity> = IndexingTestEntity::class.java

    override fun registerFileSets(
      entity: IndexingTestEntity,
      registrar: WorkspaceFileSetRegistrar,
      storage: EntityStorage,
    ) {
      for (root in entity.roots) {
        registrar.registerFileSet(root, WorkspaceFileKind.CONTENT, entity, ModuleRootData(module))
      }
    }
  }

  private data class ModuleRootData(override val module: Module) : ModuleRelatedRootData
}

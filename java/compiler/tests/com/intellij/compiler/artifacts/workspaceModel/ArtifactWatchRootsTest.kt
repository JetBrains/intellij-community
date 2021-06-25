// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.artifacts.workspaceModel

import com.intellij.compiler.artifacts.ArtifactsTestCase
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.packaging.impl.artifacts.PlainArtifactType
import com.intellij.packaging.impl.elements.FileCopyPackagingElement
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class ArtifactWatchRootsTest : ArtifactsTestCase() {

  @Rule
  var folder: TemporaryFolder = TemporaryFolder()

  override fun runInDispatchThread(): Boolean = true

  override fun setUp() {
    super.setUp()
    folder.create()
  }

  fun `test watch roots rename artifact content via workspace model`() {
    assumeTrue(WorkspaceModel.enabledForArtifacts)
    val outputDir = folder.newFolder("output")
    val sourceDir = folder.newFolder("source")

    val file = runWriteAction {
      val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceDir)!!
      val file = virtualFile.createChildData(Any(), "JustAFile")
      file
    }

    val outputVirtualUrl = VirtualFileUrlManager.getInstance(project).fromPath(outputDir.path)
    val fileVirtualUrl = VirtualFileUrlManager.getInstance(project).fromPath(file.path)
    runWriteAction {
      WorkspaceModel.getInstance(project).updateProjectModel {
        val fileCopy = it.addFileCopyPackagingElementEntity(fileVirtualUrl, null, MySource)
        val rootElement = it.addArtifactRootElementEntity(listOf(fileCopy), MySource)
        it.addArtifactEntity("MyArtifact", PlainArtifactType.ID, false, outputVirtualUrl, rootElement, MySource)
      }
    }
    runWriteAction {
      VirtualFileManagerEx.getInstance().syncRefresh()

      file.rename(this, "AnotherName")
    }

    val artifactEntity = WorkspaceModel.getInstance(project).entityStorage.current.entities(ArtifactEntity::class.java).single()
    val copyElement = artifactEntity.rootElement!!.children.single() as FileCopyPackagingElementEntity
    assertEquals("AnotherName", copyElement.filePath.fileName)
  }

  fun `test watch roots rename artifact content via bridge`() {
    assumeTrue(WorkspaceModel.enabledForArtifacts)
    val sourceDir = folder.newFolder("source")

    val file = runWriteAction {
      val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sourceDir)!!
      val file = virtualFile.createChildData(Any(), "JustAFile")
      file
    }

    runWriteAction {
      val modifiableModel = ArtifactManager.getInstance(project).createModifiableModel()
      val artifact = modifiableModel.addArtifact("MyArtifact", PlainArtifactType.getInstance())
      val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
      modifiableArtifact.rootElement.addFirstChild(FileCopyPackagingElement(file.path))
      modifiableModel.commit()
    }
    runWriteAction {
      VirtualFileManagerEx.getInstance().syncRefresh()

      file.rename(this, "AnotherName")
    }

    val resultingFilePath = (ArtifactManager.getInstance(project).artifacts[0].rootElement.children.single() as FileCopyPackagingElement).filePath
    assertTrue(resultingFilePath.endsWith("AnotherName"))
  }
  object MySource : EntitySource
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.artifacts.workspaceModel

import com.intellij.compiler.artifacts.ArtifactsTestCase
import com.intellij.java.workspace.entities.ArtifactEntity
import com.intellij.java.workspace.entities.ArtifactRootElementEntity
import com.intellij.java.workspace.entities.FileCopyPackagingElementEntity
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.packaging.impl.artifacts.PlainArtifactType
import com.intellij.packaging.impl.elements.FileCopyPackagingElement
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.testFramework.workspaceModel.updateProjectModel
import java.nio.file.Files
import java.nio.file.Path

class ArtifactWatchRootsTest : ArtifactsTestCase() {
  override fun runInDispatchThread(): Boolean = true

  fun `test watch roots rename artifact content via workspace model`() {
    val testRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Files.createDirectories(Path.of(FileUtil.getTempDirectory())))!!
    val outputDir = Files.createDirectories(Path.of(FileUtil.getTempDirectory(), "output")).toFile()
    val file = runWriteAction {
      testRoot.createChildDirectory(Any(), "source").createChildData(Any(), "JustAFile")
    }

    val workspaceModel = WorkspaceModel.getInstance(project)
    val virtualFileUrlManager = workspaceModel.getVirtualFileUrlManager()
    val outputVirtualUrl = virtualFileUrlManager.getOrCreateFromUrl(VfsUtilCore.pathToUrl(outputDir.path))
    val fileVirtualUrl = virtualFileUrlManager.getOrCreateFromUrl(VfsUtilCore.pathToUrl(file.path))
    runWriteAction {
      workspaceModel.updateProjectModel {
        it addEntity ArtifactEntity("MyArtifact", PlainArtifactType.ID, false, MySource) {
          outputUrl = outputVirtualUrl
          this.rootElement = ArtifactRootElementEntity(MySource) {
            children = listOf(FileCopyPackagingElementEntity(fileVirtualUrl, MySource))
          }
        }
      }
    }
    runWriteAction {
      testRoot.refresh(false, true)
      file.rename(this, "AnotherName")
    }

    val artifactEntity = workspaceModel.currentSnapshot.entities(ArtifactEntity::class.java).single()
    val copyElement = artifactEntity.rootElement!!.children.single() as FileCopyPackagingElementEntity
    assertEquals("AnotherName", copyElement.filePath.fileName)
  }

  fun `test watch roots rename artifact content via bridge`() {
    val testRoot = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Files.createDirectories(Path.of(FileUtil.getTempDirectory())))!!
    val file = runWriteAction {
      testRoot.createChildDirectory(Any(), "source").createChildData(Any(), "JustAFile")
    }

    runWriteAction {
      val modifiableModel = ArtifactManager.getInstance(project).createModifiableModel()
      val artifact = modifiableModel.addArtifact("MyArtifact", PlainArtifactType.getInstance())
      val modifiableArtifact = modifiableModel.getOrCreateModifiableArtifact(artifact)
      modifiableArtifact.rootElement.addFirstChild(FileCopyPackagingElement(file.path))
      modifiableModel.commit()
    }
    runWriteAction {
      testRoot.refresh(false, true)
      file.rename(this, "AnotherName")
    }

    val resultingFilePath = (ArtifactManager.getInstance(project).artifacts[0].rootElement.children.single() as FileCopyPackagingElement).filePath
    assertTrue(resultingFilePath.endsWith("AnotherName"))
  }

  object MySource : EntitySource
}

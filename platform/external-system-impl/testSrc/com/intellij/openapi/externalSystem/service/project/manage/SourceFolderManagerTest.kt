// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.storage.VersionedStorageChange
import junit.framework.TestCase
import org.assertj.core.api.BDDAssertions.then
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.io.File

class SourceFolderManagerTest: HeavyPlatformTestCase() {
  fun `test source folder is added to content root when created`() {
    val rootManager = ModuleRootManager.getInstance(module)
    val modifiableModel = rootManager.modifiableModel
    modifiableModel.addContentEntry(tempDir.createVirtualDir())
    runWriteAction {
      modifiableModel.commit()
    }

    val manager = SourceFolderManager.getInstance(project) as SourceFolderManagerImpl

    val folderUrl = ModuleRootManager.getInstance(module).contentRootUrls[0] + "/newFolder"
    val folderFile = File(VfsUtilCore.urlToPath(folderUrl))

    manager.addSourceFolder(module, folderUrl, JavaSourceRootType.SOURCE)

    val file = File(folderFile, "file.txt")
    FileUtil.writeToFile(file, "SomeContent")

    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
    manager.consumeBulkOperationsState { PlatformTestUtil.waitForFuture(it, 1000)}
    then(rootManager.contentEntries[0].sourceFolders)
      .hasSize(1)
      .extracting("url")
      .containsExactly(folderUrl)
  }

  fun `test new content root is created if source folder does not belong to existing one`() {
    val rootManager = ModuleRootManager.getInstance(module)
    val dir = createTempDir("contentEntry")
    createModuleWithContentRoot(dir)

    val manager:SourceFolderManagerImpl = SourceFolderManager.getInstance(project) as SourceFolderManagerImpl
    val folderFile = File(dir, "newFolder")
    val folderUrl = VfsUtilCore.pathToUrl(folderFile.absolutePath)

    manager.addSourceFolder(module, folderUrl, JavaSourceRootType.SOURCE)

    val file = File(folderFile, "file.txt")
    FileUtil.writeToFile(file, "SomeContent")

    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
    manager.consumeBulkOperationsState { PlatformTestUtil.waitForFuture(it, 1000)}

    then(rootManager
           .contentEntries
           .flatMap { it.sourceFolders.asList() }
           .map { it.url })
      .containsExactly(folderUrl)
  }

  fun `test update source folders execute within single storage diff`() {
    val manager = SourceFolderManager.getInstance(project) as SourceFolderManagerImpl
    val firstModuleFolder = createTempDir("foo")
    val firstModule = createModuleWithContentRoot(firstModuleFolder, "foo")
    val secondModuleFolder = createTempDir("bar")
    val secondModule = createModuleWithContentRoot(secondModuleFolder, "bar")

    var folderFile = File(firstModuleFolder, "newFolder")
    FileUtil.createDirectory(folderFile)
    val firstFolderUrl = VfsUtilCore.pathToUrl(folderFile.absolutePath)
    manager.addSourceFolder(firstModule, firstFolderUrl, JavaSourceRootType.SOURCE)

    folderFile = File(secondModuleFolder, "newFolder")
    FileUtil.createDirectory(folderFile)
    val secondFolderUrl = VfsUtilCore.pathToUrl(folderFile.absolutePath)
    manager.addSourceFolder(secondModule, secondFolderUrl, JavaSourceRootType.SOURCE)

    var notificationsCount = 0
    val version = WorkspaceModel.getInstance(project).entityStorage.version
    WorkspaceModelTopics.getInstance(project).subscribeImmediately(project.messageBus.connect(), object : WorkspaceModelChangeListener {
      override fun changed(event: VersionedStorageChange) {
        notificationsCount++
      }
    })
    LocalFileSystem.getInstance().refresh(false)
    manager.consumeBulkOperationsState { PlatformTestUtil.waitForFuture(it, 1000)}
    TestCase.assertTrue(notificationsCount == 1)
    TestCase.assertTrue(version + 1 == WorkspaceModel.getInstance(project).entityStorage.version)
  }

  private fun createModuleWithContentRoot(dir: File, moduleName: String = "topModule"): Module {
    val moduleManager = ModuleManager.getInstance(project)
    val modifiableModel = moduleManager.modifiableModel
    val newModule: Module =
      try {
        modifiableModel.newModule(dir.toPath().resolve(moduleName).toAbsolutePath(), ModuleTypeId.JAVA_MODULE)
      }
      finally {
        runWriteAction {
          modifiableModel.commit()
        }
      }

    val modifiableRootModel = ModuleRootManager.getInstance(newModule).modifiableModel
    try {
      modifiableRootModel.addContentEntry(VfsUtilCore.pathToUrl(dir.absolutePath))
    } finally {
      runWriteAction {
        modifiableRootModel.commit()
      }
    }

    return newModule
  }
}
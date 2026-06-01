// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.readAction
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class ExcludedSymlinkNotScannedTest {
  @JvmField
  @RegisterExtension
  val projectModel = ProjectModelExtension()

  @JvmField
  @RegisterExtension
  val externalRootWithSymlinkToData = TempDirectoryExtension()

  @JvmField
  @RegisterExtension
  val externalRootWithData = TempDirectoryExtension()

  @Test
  fun `dirty files scanning must not scan files via excluded symlinks`(): Unit = runBlocking {
    assumeTrue(IoTestUtil.isSymLinkCreationSupported, "Can't create symlinks")

    val project = projectModel.project
    val module = projectModel.createModule()

    val dataFile = externalRootWithData.newVirtualFile("target/data.txt", "data".toByteArray())

    val dirWithSymlinkToData = externalRootWithSymlinkToData.newVirtualDirectory("target")
    createSymlink(dirWithSymlinkToData, "data.txt", dataFile)

    val contentRoot = projectModel.baseProjectDir.newVirtualDirectory("content")
    val symlinkToDirWithSymlink = createSymlink(contentRoot, "work", dirWithSymlinkToData)


    symlinkToDirWithSymlink.refresh(false, true)
    val dataViaSymlinks = symlinkToDirWithSymlink.findChild("data.txt")!!

    ModuleRootModificationUtil.updateModel(module) { model ->
      model.addContentEntry(contentRoot).addExcludeFolder(symlinkToDirWithSymlink)
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)

    assertThat(readAction { ProjectFileIndex.getInstance(project).isExcluded(dataViaSymlinks) }).isTrue()

    val filesToScan = mutableSetOf<VirtualFile>()
    val dirtyFiles = async { listOf(contentRoot) }
    val contentIterator = ContentIterator { file -> true.also { filesToScan.add(file) } }
    DirtyFilesIndexableFilesIterator(dirtyFiles, fromOrphanQueue = false)
      .iterateFiles(project, contentIterator, VirtualFileFilter.ALL)

    assertThat(filesToScan).allMatch { file -> !file.`is`(VFileProperty.SYMLINK) }
    assertThat(filesToScan).contains(contentRoot)
  }

  private fun createSymlink(parent: VirtualFile, name: String, target: VirtualFile): VirtualFile {
    val linkPath = parent.toNioPath().resolve(name)
    val linkIoFile = IoTestUtil.createSymLink(target.path, linkPath.toString())
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(linkIoFile) ?: error("Cannot find symlink $linkIoFile")
  }

}

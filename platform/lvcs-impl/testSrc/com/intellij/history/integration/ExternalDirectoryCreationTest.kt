// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration

import com.intellij.history.core.changes.CreateEntryChange
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.TestObservation
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.useProjectAsync
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.io.path.createDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.pathString
import kotlin.io.path.writeText

/**
 * Local History registers a directory created outside the IDE together with its indexable content only.
 * It must never list the disk: the listener runs under the VFS write lock (IJPL-84970).
 */
@TestApplication
class ExternalDirectoryCreationTest {
  @JvmField
  @RegisterExtension
  val rootDir: TempDirectoryExtension = TempDirectoryExtension()

  @JvmField
  @RegisterExtension
  val targetDir: TempDirectoryExtension = TempDirectoryExtension()

  @Test
  fun `non-indexable root registers only the created directory`(): Unit = runBlocking {
    withProject { project, root ->
      assertThat(readAction { WorkspaceFileIndex.getInstance(project).isIndexable(root) }).isFalse()

      val created = recordCreated(root) {
        rootDir.newFileNio("d1/d2/d3/f.txt").writeText("f")
      }

      assertThat(created).containsExactly("${root.path}/d1")
      assertThat(LocalFileSystem.getInstance().findFileByPathIfCached("${root.path}/d1/d2")).isNull()
    }
  }

  @Test
  fun `module content root registers the whole preloaded subtree`(): Unit = runBlocking {
    withProject { project, root ->
      addModule(project, root)
      assertThat(readAction { WorkspaceFileIndex.getInstance(project).isIndexable(root) }).isTrue()

      val created = recordCreated(root) {
        rootDir.newFileNio("d1/d2/d3/f.txt").writeText("f")
      }

      assertThat(created).containsExactlyInAnyOrder(
        "${root.path}/d1",
        "${root.path}/d1/d2",
        "${root.path}/d1/d2/d3",
        "${root.path}/d1/d2/d3/f.txt",
      )
    }
  }

  @Test
  fun `symlink to directory is registered but not walked`(): Unit = runBlocking {
    assumeTrue(IoTestUtil.isSymLinkCreationSupported, "can't create symlinks")
    val target = targetDir.newDirectoryPath("target")
    target.resolve("a.txt").writeText("a")
    target.resolve("sub").createDirectories().resolve("b.txt").writeText("b")
    withProject { project, root ->
      addModule(project, root)

      val link = "${root.path}/d/link"
      val fs = LocalFileSystem.getInstance()
      // Later listeners (e.g. indexing) may load the link, so check the VFS cache right after Local History handled the events.
      val cachedAfterLocalHistory = ArrayList<String>()
      val disposable = Disposer.newDisposable()
      try {
        checkNotNull(LocalHistoryImpl.getInstanceImpl().getEventDispatcher()).addVirtualFileListener(object : BulkFileListener {
          override fun after(events: List<VFileEvent>) {
            listOf("$link/a.txt", "$link/sub", "${target.pathString}/a.txt").filterTo(cachedAfterLocalHistory) {
              fs.findFileByPathIfCached(it) != null
            }
          }
        }, disposable)

        val created = recordCreated(root) {
          rootDir.newDirectoryPath("d").resolve("link").createSymbolicLinkPointingTo(target)
        }

        assertThat(created).containsExactlyInAnyOrder("${root.path}/d", link)
        assertThat(fs.findFileByPathIfCached(link)).isNotNull()
        assertThat(cachedAfterLocalHistory).isEmpty()
      }
      finally {
        Disposer.dispose(disposable)
      }
    }
  }

  @Test
  fun `content root under excluded directory is registered, excluded files are not`(): Unit = runBlocking {
    withProject { project, root ->
      val module = addModule(project, root)
      val excluded = "${root.path}/ex"
      val innerContentRoot = "$excluded/src"
      ModuleRootModificationUtil.updateModel(module) { model ->
        model.contentEntries.single().addExcludeFolder(VfsUtilCore.pathToUrl(excluded))
        model.addContentEntry(VfsUtilCore.pathToUrl(innerContentRoot))
      }
      IndexingTestUtil.waitUntilIndexesAreReady(project)

      val created = recordCreated(root) {
        rootDir.newFileNio("ex/out/x.txt").writeText("x")
        rootDir.newFileNio("ex/src/a/A.txt").writeText("a")
      }

      assertThat(created).containsExactlyInAnyOrder(
        innerContentRoot,
        "$innerContentRoot/a",
        "$innerContentRoot/a/A.txt",
      )
    }
  }

  @Test
  fun `ignored directory is neither registered nor descended`(): Unit = runBlocking {
    withProject { project, root ->
      addModule(project, root)

      val created = recordCreated(root) {
        rootDir.newFileNio("d/CVS/x.txt").writeText("x")
        rootDir.newFileNio("d/y.txt").writeText("y")
      }

      assertThat(created).containsExactlyInAnyOrder("${root.path}/d", "${root.path}/d/y.txt")
    }
  }

  @Test
  fun `content root with an ignored name is registered with its files`(): Unit = runBlocking {
    // not CVS or another default ignored name: the default .analysisignore lines exclude those anyway
    val ignoredName = "lvcsIgnoredRoot"
    val fileTypeManager = FileTypeManagerEx.getInstanceEx()
    val originalIgnoredFiles = fileTypeManager.ignoredFilesList
    edtWriteAction { fileTypeManager.ignoredFilesList = "$originalIgnoredFiles;$ignoredName" }
    try {
      withProject { project, root ->
        val module = addModule(project, root)
        val ignoredNameRoot = "${root.path}/d/$ignoredName"
        ModuleRootModificationUtil.updateModel(module) { model ->
          model.addContentEntry(VfsUtilCore.pathToUrl(ignoredNameRoot))
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val created = recordCreated(root) {
          rootDir.newFileNio("d/$ignoredName/x.txt").writeText("x")
        }

        assertThat(created).containsExactlyInAnyOrder("${root.path}/d", ignoredNameRoot, "$ignoredNameRoot/x.txt")
      }
    }
    finally {
      edtWriteAction { fileTypeManager.ignoredFilesList = originalIgnoredFiles }
    }
  }

  private suspend fun withProject(action: suspend (Project, VirtualFile) -> Unit) {
    rootDir.newDirectoryPath(".idea") // makes the project base dir match the temp root
    ProjectUtil.openOrImportAsync(rootDir.rootPath, OpenProjectTask { createModule = false })!!.useProjectAsync { project ->
      TestObservation.awaitConfiguration(project)
      val root = checkNotNull(VfsTestUtil.findFileByCaseSensitivePath(rootDir.rootPath.pathString))
      root.children // load the root's children so the refresh reports new ones as created
      action(project, root)
    }
  }

  private suspend fun addModule(project: Project, root: VirtualFile): Module {
    val module = writeAction {
      ModuleManager.getInstance(project).newModule(rootDir.rootPath.resolve(".idea/m.iml"), EmptyModuleType.EMPTY_MODULE)
    }
    ModuleRootModificationUtil.addContentRoot(module, root) // takes the write action on EDT itself
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    return module
  }

  /** Creates files on disk, refreshes [root] and returns the paths Local History recorded as created under it */
  private fun recordCreated(root: VirtualFile, createOnDisk: () -> Unit): Set<String> {
    val before = createdUnder(root.path)
    createOnDisk()
    VfsUtil.markDirtyAndRefresh(false, true, false, root)
    return createdUnder(root.path) - before
  }

  private fun createdUnder(rootPath: String): Set<String> {
    val facade = checkNotNull(LocalHistoryImpl.getInstanceImpl().facade) { "Local History is not initialized" }
    return facade.changes
      .flatMap { it.changes }
      .filterIsInstance<CreateEntryChange>()
      .map { it.path }
      .filter { it.startsWith("$rootPath/") && !it.startsWith("$rootPath/.idea") }
      .toSet()
  }
}

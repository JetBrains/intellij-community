package com.intellij.platform.lsp.impl.platformListeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.platform.lsp.impl.LspDynamicCapabilities
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.LspServerImpl.FileChangeInfo
import com.intellij.platform.lsp.impl.LspServerManagerImpl
import com.intellij.util.containers.MultiMap
import org.eclipse.lsp4j.FileChangeType

/**
 * `LspFileListener` solves two problems: sends `didClose`/`didOpen` notifications about opened files,
 * and `didChangeWatchedFiles` notifications about all file system events for all interested servers.
 */
internal class LspFileListener : AsyncFileListener {
  override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
    if (!LspServerManagerImpl.isAnyServerRunning()) return null

    val renamedFilesAndDirs: MutableSet<VirtualFile> = LinkedHashSet()
    val deleteCreateCopyChangeEvents: MutableList<VFileEvent> = ArrayList()

    for (event in events) {
      if (event.fileSystem !is LocalFileSystem) continue

      if (event is VFileMoveEvent) {
        renamedFilesAndDirs.add(event.file)
      }
      else if (event is VFilePropertyChangeEvent && event.isRename) {
        renamedFilesAndDirs.add(event.file)
      }
      else if (event is VFileDeleteEvent ||
               event is VFileCreateEvent ||
               event is VFileCopyEvent ||
               event is VFileContentChangeEvent) {
        deleteCreateCopyChangeEvents.add(event)
      }
    }

    if (!renamedFilesAndDirs.isEmpty() || !deleteCreateCopyChangeEvents.isEmpty()) {
      return FileChangeApplier(renamedFilesAndDirs, deleteCreateCopyChangeEvents)
    }

    return null
  }

  private class FileChangeApplier(
    private val renamedFilesAndDirs: Set<VirtualFile>,
    private val deleteCreateCopyChangeEvents: List<VFileEvent>,
  ) : AsyncFileListener.ChangeApplier {
    private val serversToUpdateOpenedFiles: MutableSet<LspServerImpl> = HashSet()
    private val serverToFileChangeInfos = MultiMap.create<LspServerImpl, FileChangeInfo>()

    override fun beforeVfsChange() {
      LspServerManagerImpl.forEachRunningServerInEachProject { server ->
        val handleFileEvents = server.dynamicCapabilities.hasCapability(LspDynamicCapabilities.didChangeWatchedFiles)

        if (handleFileEvents) {
          for (event in deleteCreateCopyChangeEvents) {
            if (event is VFileDeleteEvent) {
              val file: VirtualFile = event.file
              val uri = server.descriptor.getFileUri(file)
              serverToFileChangeInfos.putValue(server, FileChangeInfo(file.path, uri, file.isDirectory, FileChangeType.Deleted))
            }
          }
        }

        for (fileOrDir in renamedFilesAndDirs) {
          if (handleFileEvents) {
            val uri = server.descriptor.getFileUri(fileOrDir)
            serverToFileChangeInfos.putValue(server, FileChangeInfo(fileOrDir.path, uri, fileOrDir.isDirectory, FileChangeType.Deleted))
          }

          val filesToClose = mutableListOf<VirtualFile>()
          server.forEachOpenedFile { openedFile: VirtualFile ->
            if (VfsUtilCore.isAncestor(fileOrDir, openedFile, false)) {
              serversToUpdateOpenedFiles.add(server)
              filesToClose.add(openedFile)
            }
          }
          filesToClose.forEach { server.documentSyncManager.close(it) }
        }
      }
    }

    override fun afterVfsChange() {
      LspServerManagerImpl.forEachRunningServerInEachProject { server ->
        if (!server.dynamicCapabilities.hasCapability(LspDynamicCapabilities.didChangeWatchedFiles)) {
          return@forEachRunningServerInEachProject
        }

        for (fileOrDir in renamedFilesAndDirs) {
          val uri = server.descriptor.getFileUri(fileOrDir)
          serverToFileChangeInfos.putValue(server, FileChangeInfo(fileOrDir.path, uri, fileOrDir.isDirectory, FileChangeType.Created))
        }

        for (event in deleteCreateCopyChangeEvents) {
          if (event is VFileContentChangeEvent) {
            val file: VirtualFile = event.file
            val uri = server.descriptor.getFileUri(file)
            serverToFileChangeInfos.putValue(server, FileChangeInfo(file.path, uri, file.isDirectory, FileChangeType.Changed))
          }
          if (event is VFileCreateEvent) {
            val file = event.getFile()
            if (file != null) {
              val uri = server.descriptor.getFileUri(file)
              serverToFileChangeInfos.putValue(server, FileChangeInfo(file.path, uri, file.isDirectory, FileChangeType.Created))
            }
          }
          if (event is VFileCopyEvent) {
            val file = event.findCreatedFile()
            if (file != null) {
              val uri = server.descriptor.getFileUri(file)
              serverToFileChangeInfos.putValue(server, FileChangeInfo(file.path, uri, file.isDirectory, FileChangeType.Created))
            }
          }
        }
      }

      if (!serverToFileChangeInfos.isEmpty) {
        ApplicationManager.getApplication().executeOnPooledThread {
          for ((server, fileChangeInfos) in serverToFileChangeInfos.entrySet()) {
            server.processFileEvents(fileChangeInfos)
          }
        }
      }

      // A file extension might have been changed.
      // A folder might have been moved out of the content root or vice versa.
      // So we need to check all opened and unsaved files and send `textDocument/didOpen` request to the server if needed.
      serversToUpdateOpenedFiles.forEach { it.documentSyncManager.openForOpenedOrUnsavedFiles() }
    }
  }
}

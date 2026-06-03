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
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.fileEvents.FileChangeInfo
import com.intellij.util.containers.MultiMap
import org.eclipse.lsp4j.FileChangeType

/**
 * `LspFileListener` solves two problems: sends `didClose`/`didOpen` notifications about opened files,
 * and `didChangeWatchedFiles` notifications about all file system events for all interested servers.
 */
internal class LspFileListener : AsyncFileListener {
  override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
    if (!LspClientManagerImpl.isAnyServerRunning()) return null

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
    private val clientsToUpdateOpenedFiles: MutableSet<LspClientImpl> = HashSet()
    private val clientToFileChangeInfos = MultiMap.create<LspClientImpl, FileChangeInfo>()

    override fun beforeVfsChange() {
      LspClientManagerImpl.forEachRunningClientInEachProject { client ->
        val handleFileEvents = client.dynamicCapabilities.hasCapability(LspDynamicCapabilities.didChangeWatchedFiles)

        if (handleFileEvents) {
          for (event in deleteCreateCopyChangeEvents) {
            if (event is VFileDeleteEvent) {
              val file: VirtualFile = event.file
              val uri = client.descriptor.getFileUri(file)
              clientToFileChangeInfos.putValue(client, FileChangeInfo(file.path, uri, file.isDirectory, FileChangeType.Deleted))
            }
          }
        }

        for (fileOrDir in renamedFilesAndDirs) {
          if (handleFileEvents) {
            val uri = client.descriptor.getFileUri(fileOrDir)
            clientToFileChangeInfos.putValue(client, FileChangeInfo(fileOrDir.path, uri, fileOrDir.isDirectory, FileChangeType.Deleted))
          }

          val filesToClose = mutableListOf<VirtualFile>()
          client.forEachOpenedFile { openedFile: VirtualFile ->
            if (VfsUtilCore.isAncestor(fileOrDir, openedFile, false)) {
              clientsToUpdateOpenedFiles.add(client)
              filesToClose.add(openedFile)
            }
          }
          filesToClose.forEach { client.documentSyncManager.close(it) }
        }
      }
    }

    override fun afterVfsChange() {
      LspClientManagerImpl.forEachRunningClientInEachProject { client ->
        if (!client.dynamicCapabilities.hasCapability(LspDynamicCapabilities.didChangeWatchedFiles)) {
          return@forEachRunningClientInEachProject
        }

        for (fileOrDir in renamedFilesAndDirs) {
          val uri = client.descriptor.getFileUri(fileOrDir)
          clientToFileChangeInfos.putValue(client, FileChangeInfo(fileOrDir.path, uri, fileOrDir.isDirectory, FileChangeType.Created))
        }

        for (event in deleteCreateCopyChangeEvents) {
          if (event is VFileContentChangeEvent) {
            val file: VirtualFile = event.file
            val uri = client.descriptor.getFileUri(file)
            clientToFileChangeInfos.putValue(client, FileChangeInfo(file.path, uri, file.isDirectory, FileChangeType.Changed))
          }
          if (event is VFileCreateEvent) {
            val file = event.getFile()
            if (file != null) {
              val uri = client.descriptor.getFileUri(file)
              clientToFileChangeInfos.putValue(client, FileChangeInfo(file.path, uri, file.isDirectory, FileChangeType.Created))
            }
          }
          if (event is VFileCopyEvent) {
            val file = event.findCreatedFile()
            if (file != null) {
              val uri = client.descriptor.getFileUri(file)
              clientToFileChangeInfos.putValue(client, FileChangeInfo(file.path, uri, file.isDirectory, FileChangeType.Created))
            }
          }
        }
      }

      if (!clientToFileChangeInfos.isEmpty) {
        ApplicationManager.getApplication().executeOnPooledThread {
          for ((client, fileChangeInfos) in clientToFileChangeInfos.entrySet()) {
            client.watchedFiles.processFileEvents(fileChangeInfos)
          }
        }
      }

      // A file extension might have been changed.
      // A folder might have been moved out of the content root or vice versa.
      // So we need to check all opened and unsaved files and send `textDocument/didOpen` request to the server if needed.
      clientsToUpdateOpenedFiles.forEach { it.documentSyncManager.openForOpenedOrUnsavedFiles() }
    }
  }
}

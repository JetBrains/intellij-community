package com.intellij.platform.lsp.impl.fileEvents

import com.intellij.platform.lsp.impl.LspDynamicCapabilities
import com.intellij.platform.lsp.impl.LspClientImpl
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions
import org.eclipse.lsp4j.FileEvent

/**
 * Sends `workspace/didChangeWatchedFiles` notifications about file-system events the [client] is interested in.
 *
 * The platform listeners (e.g. `LspFileListener`) collect VFS events and dispatch them here.
 */
internal class LspWatchedFiles(private val client: LspClientImpl) {
  fun processFileEvents(fileChangeInfos: Collection<FileChangeInfo>) {
    val options = client.dynamicCapabilities.getCapabilityRegistrationOptions(LspDynamicCapabilities.didChangeWatchedFiles)
    val eventsOfInterest = fileChangeInfos.mapNotNull { getLsp4jFileEvent(it, options) }
    if (eventsOfInterest.isNotEmpty()) {
      client.sendNotification { it.workspaceService.didChangeWatchedFiles(DidChangeWatchedFilesParams(eventsOfInterest)) }
    }
  }

  private fun getLsp4jFileEvent(fileChangeInfo: FileChangeInfo, options: List<DidChangeWatchedFilesRegistrationOptions>): FileEvent? {
    for (option in options) {
      for (watcher in option.watchers) {
        if (!fileChangeInfo.doesFileWatcherKindMatchFileChangeType(watcher.kind)) continue

        if (watcher.globPattern.isLeft) {
          val globPattern = watcher.globPattern.left!!
          if (client.globMatcher.pathMatches(fileChangeInfo.path, fileChangeInfo.isDirectory, globPattern, null)) {
            return FileEvent(fileChangeInfo.uri, fileChangeInfo.changeType)
          }
        }
        else {
          val relativePattern = watcher.globPattern.right!!
          val baseUri = relativePattern.baseUri.map({ it.uri }, { it })
          val baseDir = client.descriptor.findFileByUri(baseUri)
          if (baseDir != null && baseDir.isDirectory) {
            val globPattern = relativePattern.pattern
            if (client.globMatcher.pathMatches(fileChangeInfo.path, fileChangeInfo.isDirectory, globPattern, baseDir.path)) {
              return FileEvent(fileChangeInfo.uri, fileChangeInfo.changeType)
            }
          }
        }
      }
    }
    return null
  }
}

package com.intellij.platform.lsp.impl.fileEvents

import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.WatchKind

internal class FileChangeInfo(
  val path: String,
  val uri: String,
  val isDirectory: Boolean,
  val changeType: FileChangeType,
) {
  fun doesFileWatcherKindMatchFileChangeType(watchKind: Int?): Boolean {
    // https://microsoft.github.io/language-server-protocol/specification/#fileSystemWatcher
    if (watchKind == null) {
      // null is equivalent to WatchKind.Create | WatchKind.Change | WatchKind.Delete
      return true
    }

    return when (changeType) {
      FileChangeType.Created -> (watchKind and WatchKind.Create) != 0
      FileChangeType.Changed -> (watchKind and WatchKind.Change) != 0
      FileChangeType.Deleted -> (watchKind and WatchKind.Delete) != 0
    }
  }
}

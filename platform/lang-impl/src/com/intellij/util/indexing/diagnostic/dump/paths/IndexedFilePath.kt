package com.intellij.util.indexing.diagnostic.dump.paths

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class IndexedFilePath(
  val originalFileSystemId: Int,
  val fileType: String?,
  val substitutedFileType: String?,
  val fileSize: Long?,
  val originalFileUrl: String,
  val portableFilePath: PortableFilePath,
  val filePropertyPusherValues: Map<String /* Pusher presentable name */, String /* Presentable file immediate pushed value */>,
  val contentHash: String,
  val indexedFileHash: String
) {
  override fun toString(): String = buildString {
    appendLine("File URL = $originalFileUrl")
    appendLine("File ID = $originalFileSystemId")
    if (fileSize == null) {
      appendLine("This is a directory")
    } else {
      appendLine("File size = $fileSize")
    }
    if (fileType != null) {
      appendLine("File type = $fileType")
    }
    if (substitutedFileType != null) {
      appendLine("Substituted file type = $substitutedFileType")
    }
    appendLine("Portable path = ${portableFilePath.presentablePath}")
    append("File property pusher values: ")
    if (filePropertyPusherValues.isNotEmpty()) {
      appendLine()
      for ((key, value) in filePropertyPusherValues) {
        appendLine("  $key -> $value")
      }
    } else {
      appendLine("<empty>")
    }
  }
}
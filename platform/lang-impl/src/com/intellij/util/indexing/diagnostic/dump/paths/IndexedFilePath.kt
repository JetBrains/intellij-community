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
    appendln("File URL = $originalFileUrl")
    appendln("File ID = $originalFileSystemId")
    if (fileSize == null) {
      appendln("This is a directory")
    } else {
      appendln("File size = $fileSize")
    }
    if (fileType != null) {
      appendln("File type = $fileType")
    }
    if (substitutedFileType != null) {
      appendln("Substituted file type = $substitutedFileType")
    }
    appendln("Portable path = ${portableFilePath.presentablePath}")
    append("File property pusher values: ")
    if (filePropertyPusherValues.isNotEmpty()) {
      appendln()
      for ((key, value) in filePropertyPusherValues) {
        appendln("  $key -> $value")
      }
    } else {
      appendln("<empty>")
    }
  }
}
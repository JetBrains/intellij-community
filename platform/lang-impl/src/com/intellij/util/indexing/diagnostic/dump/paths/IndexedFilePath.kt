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
)
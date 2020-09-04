package com.intellij.util.indexing.diagnostic.dump

import com.intellij.util.indexing.diagnostic.dump.paths.IndexedFilePath

data class IndexContentDiagnostic(
  val allIndexedFilePaths: List<IndexedFilePath>,
  /**
   * Paths to indexed files from *unsupported* file systems (currently, only local and archive file systems are supported).
   */
  val filesFromUnsupportedFileSystems: List<IndexedFilePath>,
  /**
   * Keys - debug name of indexable file provider that schedules a set of files for indexing.
   * Values - IDs of files that were scheduled for indexing by a provider.
   */
  val projectIndexedFileProviderDebugNameToFileIds: Map<String, Set<Int>>,
  /**
   * Hashes of contents of indexed files in Base64 format OR
   * [TOO_LARGE_FILE] if the file is too large for in-memory loading OR
   * [FAILED_TO_LOAD] if the file couldn't be loaded
   */
  val indexedFileHashes: Map</* IndexedFilePath.originalFileSystemId */ Int, String>
) {
  companion object {
    const val TOO_LARGE_FILE = "<TOO LARGE>"
    const val FAILED_TO_LOAD = "<FAILED TO LOAD: %s>"

    @JvmStatic
    fun main(args: Array<String>) {
      println(FAILED_TO_LOAD.format("axaxa"))
    }
  }
}
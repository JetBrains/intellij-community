// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.intellij.openapi.progress.ProgressIndicator
import com.jetbrains.plugin.blockmap.core.BlockMap
import com.jetbrains.plugin.blockmap.core.Chunk
import com.jetbrains.plugin.blockmap.core.ChunkMerger
import java.io.*


class PluginChunkMerger(
  private val oldFile: File,
  oldBlockMap: BlockMap = BlockMap(oldFile.inputStream()),
  newBlockMap: BlockMap,
  private val indicator: ProgressIndicator
) : ChunkMerger(oldFile, oldBlockMap, newBlockMap) {
  private val newFileSize: Int = newBlockMap.chunks.sumBy { chunk -> chunk.length }
  private var wroteBytes: Int = 0

  override fun merge(output: OutputStream, newChunkDataSource: Iterator<ByteArray>) {
    indicator.checkCanceled()
    indicator.isIndeterminate = newFileSize <= 0
    super.merge(output, newChunkDataSource)
  }

  override fun downloadChunkFromNewData(newChunk: Chunk, newChunkDataSource: Iterator<ByteArray>, output: OutputStream) {
    super.downloadChunkFromNewData(newChunk, newChunkDataSource, output)
    wroteBytes += newChunk.length
    setIndicatorFraction()
  }

  override fun downloadChunkFromOldData(oldChunk: Chunk, oldFileRAF: RandomAccessFile, output: OutputStream) {
    super.downloadChunkFromOldData(oldChunk, oldFileRAF, output)
    wroteBytes += oldChunk.length
    setIndicatorFraction()
  }

  private fun setIndicatorFraction() {
    indicator.checkCanceled()
    indicator.fraction = wroteBytes.toDouble() / newFileSize.toDouble()
  }
}
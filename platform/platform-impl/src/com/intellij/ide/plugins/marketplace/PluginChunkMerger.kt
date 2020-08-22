// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.marketplace

import com.intellij.openapi.progress.ProgressIndicator
import com.jetbrains.plugin.blockmap.core.BlockMap
import com.jetbrains.plugin.blockmap.core.Chunk
import com.jetbrains.plugin.blockmap.core.ChunkMerger
import java.io.*


class PluginChunkMerger(
  oldFile: File,
  oldBlockMap: BlockMap,
  newBlockMap: BlockMap,
  private val indicator: ProgressIndicator
) : ChunkMerger(oldFile, oldBlockMap, newBlockMap) {
  private val newFileSize: Int = newBlockMap.chunks.sumBy { chunk -> chunk.length }
  private var progress: Int = 0

  override fun merge(output: OutputStream, newChunkDataSource: Iterator<ByteArray>) {
    indicator.checkCanceled()
    indicator.isIndeterminate = newFileSize <= 0
    super.merge(output, newChunkDataSource)
  }

  override fun downloadChunkFromNewData(newChunk: Chunk, newChunkDataSource: Iterator<ByteArray>, output: OutputStream) {
    super.downloadChunkFromNewData(newChunk, newChunkDataSource, output)
    progress += newChunk.length
    setIndicatorFraction()
  }

  override fun downloadChunkFromOldData(oldChunk: Chunk, oldFileRAF: RandomAccessFile, output: OutputStream) {
    super.downloadChunkFromOldData(oldChunk, oldFileRAF, output)
    progress += oldChunk.length
    setIndicatorFraction()
  }

  private fun setIndicatorFraction() {
    indicator.checkCanceled()
    indicator.fraction = progress.toDouble() / newFileSize.toDouble()
  }
}
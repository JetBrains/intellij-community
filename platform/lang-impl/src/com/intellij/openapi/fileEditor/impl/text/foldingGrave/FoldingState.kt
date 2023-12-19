// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text.foldingGrave

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.editor.impl.FoldingModelImpl.ZOMBIE_REGION_KEY
import com.intellij.openapi.fileEditor.impl.text.TextEditorCache.Companion.contentHash
import com.intellij.openapi.fileEditor.impl.text.VersionedExternalizer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import com.intellij.util.io.IOUtil.readUTF
import com.intellij.util.io.IOUtil.writeUTF
import java.io.DataInput
import java.io.DataOutput

internal class FoldingState(private val contentHash: Int, private val regions: List<RegionState>) {

  data class RegionState(
    val startOffset: Int,
    val endOffset: Int,
    val placeholderText: String,
    val neverExpands: Boolean,
    val isExpanded: Boolean,
  ) {
    companion object {
      fun create(region: FoldRegion) = RegionState(
        region.startOffset,
        region.endOffset,
        region.placeholderText,
        region.shouldNeverExpand(),
        region.isExpanded,
      )

      fun read(input: DataInput): RegionState {
        val start = readINT(input)
        val end = readINT(input)
        val placeholder = readUTF(input)
        val neverExpands = input.readBoolean()
        val isExpanded = input.readBoolean()
        return RegionState(start, end, placeholder, neverExpands, isExpanded)
      }
    }

    fun save(output: DataOutput) {
      writeINT(output, startOffset)
      writeINT(output, endOffset)
      writeUTF(output, placeholderText)
      output.writeBoolean(neverExpands)
      output.writeBoolean(isExpanded)
    }

    override fun toString() = "($startOffset-$endOffset, '$placeholderText', ${(if (isExpanded) "-" else "+")})"
  }

  @RequiresEdt
  fun applyState(document: Document, foldingModel: FoldingModelEx) {
    if (contentHash == document.contentHash()) {
      foldingModel.runBatchFoldingOperationDoNotCollapseCaret {
        var zombieRaised = false
        for ((start, end, placeholder, neverExpands, isExpanded) in regions) {
          val region = foldingModel.createFoldRegion(start, end, placeholder, null, neverExpands)
          if (region != null) {
            region.isExpanded = isExpanded
            region.putUserData(ZOMBIE_REGION_KEY, true)
            zombieRaised = true
          }
        }
        if (zombieRaised && foldingModel is FoldingModelImpl) {
          foldingModel.isZombieRaised.set(true)
        }
      }
      logger.debug { "restored $this for $document" }
    } else {
      logger.debug { "content hash missing $this for document $document" }
    }
  }

  object FoldingStateExternalizer : VersionedExternalizer<FoldingState> {
    override fun serdeVersion() = 2
    override fun save(output: DataOutput, value: FoldingState) = value.save(output)
    override fun read(input: DataInput) = FoldingState.read(input)
  }

  companion object {
    private val logger: Logger = Logger.getInstance(FoldingState::class.java)

    fun create(contentHash: Int, foldRegions: Array<FoldRegion>): FoldingState {
      val regions = foldRegions.map { RegionState.create(it) }.toList()
      return FoldingState(contentHash, regions)
    }

    private fun read(input: DataInput): FoldingState {
      val contentHash = readINT(input)
      val regionCount = readINT(input)
      val regions = ArrayList<RegionState>(regionCount)
      repeat(regionCount) {
        regions.add(RegionState.read(input))
      }
      return FoldingState(contentHash, regions)
    }
  }

  private fun save(output: DataOutput) {
    writeINT(output, contentHash)
    writeINT(output, regions.size)
    for (region in regions) {
      region.save(output)
    }
  }

  override fun toString() = "FoldingState(${Integer.toHexString(contentHash)}, regions=${regions.size})"
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text.foldingGrave

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.editor.impl.FoldingModelImpl.ZOMBIE_REGION_KEY
import com.intellij.openapi.fileEditor.impl.text.TextEditorCache.Companion.contentHash
import com.intellij.openapi.fileEditor.impl.text.VersionedExternalizer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.DataInputOutputUtil.*
import com.intellij.util.io.IOUtil.readUTF
import com.intellij.util.io.IOUtil.writeUTF
import java.io.DataInput
import java.io.DataOutput

internal class FoldingState(
  private val contentHash: Int,
  private val regions: List<RegionState>,
  private val groupedRegions: Map<Long, List<RegionState>>,
) {

  data class RegionState(
    val startOffset: Int,
    val endOffset: Int,
    val placeholderText: String,
    val groupId: Long?,
    val neverExpands: Boolean,
    val isExpanded: Boolean,
  ) {
    companion object {
      fun create(region: FoldRegion) = RegionState(
        region.startOffset,
        region.endOffset,
        region.placeholderText,
        region.group?.id,
        region.shouldNeverExpand(),
        region.isExpanded,
      )

      fun read(input: DataInput): RegionState {
        val start = readINT(input)
        val end = readINT(input)
        val placeholder = readUTF(input)
        val groupId = readGroupId(input)
        val neverExpands = input.readBoolean()
        val isExpanded = input.readBoolean()
        return RegionState(start, end, placeholder, groupId, neverExpands, isExpanded)
      }

      private fun readGroupId(input: DataInput): Long? {
        return if (input.readBoolean()) {
          readLONG(input)
        } else {
          null
        }
      }
    }

    fun save(output: DataOutput) {
      writeINT(output, startOffset)
      writeINT(output, endOffset)
      writeUTF(output, placeholderText)
      writeGroupId(output)
      output.writeBoolean(neverExpands)
      output.writeBoolean(isExpanded)
    }

    private fun writeGroupId(output: DataOutput) {
      if (groupId == null) {
        output.writeBoolean(false)
      } else {
        output.writeBoolean(true)
        writeLONG(output, groupId)
      }
    }

    override fun toString(): String {
      val groupStr = if (groupId == null) "" else " $groupId,"
      return "($startOffset-$endOffset,$groupStr '$placeholderText', ${(if (isExpanded) "-" else "+")})"
    }
  }

  @RequiresEdt
  fun applyState(document: Document, foldingModel: FoldingModelEx): Boolean {
    if (contentHash == document.contentHash()) {
      foldingModel.runBatchFoldingOperationDoNotCollapseCaret {
        val applied1 = applyRegions(foldingModel)
        val applied2 = applyGroupedRegions(foldingModel)
        setZombieRaised(foldingModel, applied1 || applied2)
      }
      foldingModel.clearDocumentRangesModificationStatus()
      logger.debug { "restored $this for $document" }
      return true
    } else {
      logger.debug { "content hash missing $this for document $document" }
      return false
    }
  }

  private fun applyRegions(foldingModel: FoldingModelEx): Boolean {
    var appliedAny = false
    for (region in regions) {
      assert(region.groupId == null) { "regions with non-null groupId should be handled as a grouped region" }
      val applied = applyRegion(foldingModel, region, group = null)
      appliedAny = appliedAny || applied
    }
    return appliedAny
  }

  private fun applyGroupedRegions(foldingModel: FoldingModelEx): Boolean {
    var appliedAny = false
    for ((groupId: Long, regions: List<RegionState>) in groupedRegions) {
      val group = FoldingGroup.newGroup("zombie-group")
      for (region in regions) {
        assert(region.groupId == groupId) { "folding group mismatch: expected $groupId, action ${region.groupId}" }
        val applied = applyRegion(foldingModel, region, group)
        appliedAny = appliedAny || applied
      }
    }
    return appliedAny
  }

  private fun applyRegion(foldingModel: FoldingModelEx, regionState: RegionState, group: FoldingGroup?): Boolean {
    val (start, end, placeholder, _, neverExpands, isExpanded) = regionState
    val region: FoldRegion? = foldingModel.createFoldRegion(start, end, placeholder, group, neverExpands)
    if (region != null) {
      if (region.isExpanded != isExpanded) {
        region.isExpanded = isExpanded
      }
      region.putUserData(ZOMBIE_REGION_KEY, true)
      return true
    }
    return false
  }

  private fun setZombieRaised(foldingModel: FoldingModelEx, zombieRaised: Boolean) {
    if (zombieRaised && foldingModel is FoldingModelImpl) {
      foldingModel.isZombieRaised.set(true)
    }
  }

  object FoldingStateExternalizer : VersionedExternalizer<FoldingState> {
    override fun serdeVersion(): Int = 3
    override fun save(output: DataOutput, value: FoldingState) = value.save(output)
    override fun read(input: DataInput): FoldingState = FoldingState.read(input)
  }

  companion object {
    private val logger: Logger = Logger.getInstance(FoldingState::class.java)

    fun create(contentHash: Int, foldRegions: List<FoldRegion>): FoldingState {
      val regions = ArrayList<RegionState>()
      val groupedRegions = HashMap<Long, MutableList<RegionState>>()
      for (foldRegion in foldRegions) {
        putRegionState(RegionState.create(foldRegion), regions, groupedRegions)
      }
      return FoldingState(contentHash, regions, groupedRegions)
    }

    private fun read(input: DataInput): FoldingState {
      val contentHash = readINT(input)
      val regionCount = readINT(input)
      val regions = ArrayList<RegionState>()
      val groupedRegions = HashMap<Long, MutableList<RegionState>>()
      repeat(regionCount) {
        putRegionState(RegionState.read(input), regions, groupedRegions)
      }
      return FoldingState(contentHash, regions, groupedRegions)
    }

    private fun putRegionState(
      region: RegionState,
      regions: MutableList<RegionState>,
      groupedRegions: MutableMap<Long, MutableList<RegionState>>,
    ) {
      val groupId: Long? = region.groupId
      if (groupId == null) {
        regions.add(region)
      } else {
        groupedRegions.computeIfAbsent(groupId) {
          mutableListOf()
        }.add(region)
      }
    }
  }

  private fun save(output: DataOutput) {
    writeINT(output, contentHash)
    writeRegionCount(output)
    writeRegions(output)
    writeGroupedRegions(output)
  }

  private fun writeRegionCount(output: DataOutput) {
    val regionCount = regions.size + groupedRegions.values.sumOf { it.size }
    writeINT(output, regionCount)
  }

  private fun writeRegions(output: DataOutput) {
    writeRegions(output, regions)
  }

  private fun writeGroupedRegions(output: DataOutput) {
    for ((_, regions) in groupedRegions) {
      writeRegions(output, regions)
    }
  }

  private fun writeRegions(output: DataOutput, regions: List<RegionState>) {
    for (region in regions) {
      region.save(output)
    }
  }

  override fun toString(): String {
    return "FoldingState(${Integer.toHexString(contentHash)}, regions=${regions.size}, groupedRegions=${groupedRegions.size})"
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.editor.impl.FoldingModelImpl.ZOMBIE_REGION_KEY
import com.intellij.openapi.editor.impl.zombie.Zombie
import com.intellij.util.concurrency.annotations.RequiresEdt

internal class CodeFoldingZombie(
  val regions: List<CodeFoldingRegion>,
  val groupedRegions: Map<Long, List<CodeFoldingRegion>>,
) : Zombie {

  companion object {
    private val logger: Logger = logger<CodeFoldingZombie>()

    fun create(foldRegions: List<FoldRegion>): CodeFoldingZombie {
      val regions = ArrayList<CodeFoldingRegion>()
      val groupedRegions = HashMap<Long, MutableList<CodeFoldingRegion>>()
      for (foldRegion in foldRegions) {
        val regionState = CodeFoldingRegion(
          foldRegion.startOffset,
          foldRegion.endOffset,
          foldRegion.placeholderText,
          foldRegion.group?.id,
          foldRegion.shouldNeverExpand(),
          foldRegion.isExpanded,
        )
        putRegion(regionState, regions, groupedRegions)
      }
      return CodeFoldingZombie(regions, groupedRegions)
    }

    fun putRegion(
      region: CodeFoldingRegion,
      regions: MutableList<CodeFoldingRegion>,
      groupedRegions: MutableMap<Long, MutableList<CodeFoldingRegion>>,
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

  fun isEmpty(): Boolean {
    return regions.isEmpty() && groupedRegions.isEmpty()
  }

  @RequiresEdt
  fun applyState(document: Document, foldingModel: FoldingModelEx) {
    foldingModel.runBatchFoldingOperationDoNotCollapseCaret {
      val applied1 = applyRegions(foldingModel)
      val applied2 = applyGroupedRegions(foldingModel)
      setZombieRaised(foldingModel, applied1 || applied2)
    }
    foldingModel.clearDocumentRangesModificationStatus()
    logger.debug { "restored $this for $document" }
  }

  private fun applyRegions(foldingModel: FoldingModelEx): Boolean {
    var appliedAny = false
    for (region in regions) {
      assert(region.groupId == null) {
        "regions with non-null groupId should be handled as a grouped region"
      }
      val applied = applyRegion(foldingModel, region, group = null)
      appliedAny = appliedAny || applied
    }
    return appliedAny
  }

  private fun applyGroupedRegions(foldingModel: FoldingModelEx): Boolean {
    var appliedAny = false
    for ((groupId, regions) in groupedRegions) {
      val group = FoldingGroup.newGroup("zombie-group")
      for (region in regions) {
        assert(region.groupId == groupId) {
          "folding group mismatch: expected $groupId, action ${region.groupId}"
        }
        val applied = applyRegion(foldingModel, region, group)
        appliedAny = appliedAny || applied
      }
    }
    return appliedAny
  }

  private fun applyRegion(foldingModel: FoldingModelEx, regionState: CodeFoldingRegion, group: FoldingGroup?): Boolean {
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

  override fun toString(): String {
    return "CodeFoldingZombie(regions=${regions.size}, groupedRegions=${groupedRegions.size})"
  }
}

internal data class CodeFoldingRegion(
  val startOffset: Int,
  val endOffset: Int,
  val placeholderText: String,
  val groupId: Long?,
  val neverExpands: Boolean,
  val isExpanded: Boolean,
) {
  override fun toString(): String {
    val groupStr = if (groupId == null) "" else " $groupId,"
    return "($startOffset-$endOffset,$groupStr '$placeholderText', ${(if (isExpanded) "-" else "+")})"
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl

import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.impl.FoldingKeys.ZOMBIE_REGION_KEY
import com.intellij.openapi.editor.impl.zombie.CodeFoldingZombieUtils
import com.intellij.openapi.editor.impl.zombie.LimbedZombie
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt


internal class CodeFoldingZombie(
  limbs: List<FoldLimb>,
) : LimbedZombie<FoldLimb>(limbs) {

  private val regions: List<FoldLimb> = limbs.filter { it.groupId == null }
  private val groupedRegions: Map<Long, List<FoldLimb>> = limbs.filter { it.groupId != null }.groupBy { it.groupId!! }

  @RequiresEdt
  fun applyState(editor: EditorEx) {
    ThreadingAssertions.assertEventDispatchThread()
    val foldingModel = editor.foldingModel
    foldingModel.runBatchFoldingOperationDoNotCollapseCaret {
      val applied1 = applyRegions(foldingModel)
      val applied2 = applyGroupedRegions(foldingModel)
      CodeFoldingZombieUtils.setZombieRaised(editor, applied1 || applied2)
    }
    foldingModel.clearDocumentRangesModificationStatus()
  }

  private fun applyRegions(foldingModel: FoldingModelEx): Boolean {
    var appliedAny = false
    for (region in regions) {
      if (region.groupId != null) {
        error("regions with non-null groupId should be handled as a grouped region")
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
        if (region.groupId != groupId) {
          error("folding group mismatch: expected $groupId, action ${region.groupId}")
        }
        val applied = applyRegion(foldingModel, region, group)
        appliedAny = appliedAny || applied
      }
    }
    return appliedAny
  }

  private fun applyRegion(foldingModel: FoldingModelEx, regionState: FoldLimb, group: FoldingGroup?): Boolean {
    val (start, end, placeholder, _, neverExpands, isExpanded, isCollapsedByDefault, isFrontendCreated) = regionState
    val region: FoldRegion? = foldingModel.createFoldRegion(start, end, placeholder, group, neverExpands)
    if (region != null) {
      if (region.isExpanded != isExpanded) {
        region.isExpanded = isExpanded
      }
      region.putUserData(ZOMBIE_REGION_KEY, true)
      CodeFoldingManagerImpl.setCollapsedByDefault(region, isCollapsedByDefault)
      if (isFrontendCreated) {
        CodeFoldingManagerImpl.markAsFrontendCreated(region)
      }
      return true
    }
    return false
  }

  override fun toString(): String {
    return "CodeFoldingZombie(regions=${regions.size}, groupedRegions=${groupedRegions.size})"
  }
}

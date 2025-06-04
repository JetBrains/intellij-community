// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.impl.FoldingKeys.AUTO_CREATED_ZOMBIE
import com.intellij.openapi.editor.impl.FoldingKeys.ZOMBIE_REGION_KEY
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.editor.impl.zombie.Zombie
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.text.BreakIterator
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
        val regionState = CodeFoldingRegion.create(
          foldRegion.startOffset,
          foldRegion.endOffset,
          foldRegion.placeholderText,
          foldRegion.group?.id,
          foldRegion.shouldNeverExpand(),
          foldRegion.isExpanded,
          CodeFoldingManagerImpl.isAutoCreated(foldRegion)
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
    val (start, end, placeholder, _, neverExpands, isExpanded, isAutoCreated) = regionState
    val region: FoldRegion? = foldingModel.createFoldRegion(start, end, placeholder, group, neverExpands)
    if (region != null) {
      if (region.isExpanded != isExpanded) {
        region.isExpanded = isExpanded
      }
      region.putUserData(ZOMBIE_REGION_KEY, true)
      if (isAutoCreated) {
        region.putUserData(AUTO_CREATED_ZOMBIE, true)
      }
      return true
    }
    return false
  }

  private fun setZombieRaised(foldingModel: FoldingModelEx, zombieRaised: Boolean) {
    if (zombieRaised && foldingModel is FoldingModelImpl) {
      foldingModel.isZombieRaised.set(true)
      foldingModel.isAutoCreatedZombieRaised.set(true)
    }
  }

  override fun toString(): String {
    return "CodeFoldingZombie(regions=${regions.size}, groupedRegions=${groupedRegions.size})"
  }
}

internal sealed class CodeFoldingRegion {
  abstract val startOffset: Int
  abstract val endOffset: Int
  abstract val placeholderText: String
  abstract val groupId: Long?
  abstract val neverExpands: Boolean
  abstract val isExpanded: Boolean
  abstract val isAutoCreated: Boolean
  abstract val saveType: SaveType

  operator fun component1(): Int = startOffset
  operator fun component2(): Int = endOffset
  operator fun component3(): String = placeholderText
  operator fun component4(): Long? = groupId
  operator fun component5(): Boolean = neverExpands
  operator fun component6(): Boolean = isExpanded
  operator fun component7(): Boolean = isAutoCreated


  class PlainTextCodeFoldingRegion(
    override val startOffset: Int,
    override val endOffset: Int,
    override val groupId: Long?,
    override val neverExpands: Boolean,
    override val isExpanded: Boolean,
    override val isAutoCreated: Boolean,
    override val placeholderText: String,
  ) : CodeFoldingRegion() {
    override val saveType: SaveType
      get() = SaveType.PlainText
  }

  class SecretPlaceholderCodeFoldingRegion(
    override val startOffset: Int,
    override val endOffset: Int,
    override val groupId: Long?,
    override val neverExpands: Boolean,
    override val isExpanded: Boolean,
    override val isAutoCreated: Boolean,
    val placeholderLength: Int,
  ) : CodeFoldingRegion() {
    companion object {
      const val PLACEHOLDER_SYMBOL = " "
    }

    override val placeholderText: String
      get() = PLACEHOLDER_SYMBOL.repeat(placeholderLength)

    override val saveType: SaveType
      get() = SaveType.SecretPlaceholder
  }

  override fun toString(): String {
    val groupStr = if (groupId == null) "" else " $groupId,"
    return "($startOffset-$endOffset,$groupStr '$placeholderText', ${(if (isExpanded) "-" else "+")}, ${if (isAutoCreated) "AUTO" else "MANUAL"})"
  }

  companion object {
    fun create(
      startOffset: Int,
      endOffset: Int,
      @NlsSafe placeholderText: String,
      id: Long?,
      shouldNeverExpand: Boolean,
      expanded: Boolean,
      autoCreated: Boolean,
    ) = if (Registry.`is`("cache.folding.model.hide.placeholder")) {
      SecretPlaceholderCodeFoldingRegion(
        startOffset,
        endOffset,
        id,
        shouldNeverExpand,
        expanded,
        autoCreated,
        placeholderText.graphemeCount()
      )
    }
    else {
      PlainTextCodeFoldingRegion(
        startOffset,
        endOffset,
        id,
        shouldNeverExpand,
        expanded,
        autoCreated,
        placeholderText
      )
    }
  }

  enum class SaveType(val value: Byte) {
    PlainText(0),
    SecretPlaceholder(1)
  }
}

internal fun String.graphemeCount(): Int {
  val iterator = BreakIterator.getCharacterInstance()
  iterator.setText(this)

  var count = 0
  while (iterator.next() != BreakIterator.DONE) {
    count++
  }

  return count
}

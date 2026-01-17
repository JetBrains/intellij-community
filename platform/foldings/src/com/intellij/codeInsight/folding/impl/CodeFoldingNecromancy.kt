// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl

import com.intellij.openapi.editor.impl.zombie.AbstractNecromancy
import java.io.DataInput
import java.io.DataOutput


internal object CodeFoldingNecromancy : AbstractNecromancy<CodeFoldingZombie>(spellLevel=2, isDeepBury=false) {

  override fun buryZombie(grave: DataOutput, zombie: CodeFoldingZombie) {
    writeRegionCount(grave, zombie)
    writeRegions(grave, zombie)
    writeGroupedRegions(grave, zombie)
  }

  private fun writeRegionCount(grave: DataOutput, zombie: CodeFoldingZombie) {
    val regionCount = zombie.regions.size + zombie.groupedRegions.values.sumOf { it.size }
    writeInt(grave, regionCount)
  }

  private fun writeRegions(grave: DataOutput, zombie: CodeFoldingZombie) {
    for (region in zombie.regions) {
      writeRegion(grave, region)
    }
  }

  private fun writeGroupedRegions(grave: DataOutput, zombie: CodeFoldingZombie) {
    for ((_, regions) in zombie.groupedRegions) {
      for (region in regions) {
        writeRegion(grave, region)
      }
    }
  }

  private fun writeRegion(grave: DataOutput, region: CodeFoldingRegion) {
    writeInt(grave, region.startOffset)
    writeInt(grave, region.endOffset)
    writeString(grave, region.placeholderText)
    writeLongNullable(grave, region.groupId)
    writeBool(grave, region.neverExpands)
    writeBool(grave, region.isExpanded)
    writeBool(grave, region.isCollapsedByDefault)
    writeBool(grave, region.isFrontendCreated)
  }

  override fun exhumeZombie(grave: DataInput): CodeFoldingZombie {
    val regionCount = readInt(grave)
    val regions = ArrayList<CodeFoldingRegion>()
    val groupedRegions = HashMap<Long, MutableList<CodeFoldingRegion>>()
    repeat(regionCount) {
      val region = read(grave)
      CodeFoldingZombie.putRegion(region, regions, groupedRegions)
    }
    return CodeFoldingZombie(regions, groupedRegions)
  }

  private fun read(grave: DataInput): CodeFoldingRegion {
    val startOffset:              Int = readInt(grave)
    val endOffset:                Int = readInt(grave)
    val placeholderText:       String = readString(grave)
    val groupId:                Long? = readLongNullable(grave)
    val neverExpands:         Boolean = readBool(grave)
    val isExpanded:           Boolean = readBool(grave)
    val isCollapsedByDefault: Boolean = readBool(grave)
    val isFrontendCreated:    Boolean = readBool(grave)
    return CodeFoldingRegion(
      startOffset,
      endOffset,
      placeholderText,
      groupId,
      neverExpands,
      isExpanded,
      isCollapsedByDefault,
      isFrontendCreated,
    )
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl

import com.intellij.codeInsight.folding.impl.CodeFoldingZombie.Companion.putRegion
import com.intellij.openapi.editor.impl.zombie.Necromancy
import com.intellij.util.io.DataInputOutputUtil.*
import com.intellij.util.io.IOUtil.readUTF
import com.intellij.util.io.IOUtil.writeUTF
import java.io.DataInput
import java.io.DataOutput

internal object CodeFoldingNecromancy : Necromancy<CodeFoldingZombie> {

  override fun spellLevel(): Int = 0

  override fun isDeepBury(): Boolean = false

  override fun buryZombie(grave: DataOutput, zombie: CodeFoldingZombie) {
    writeRegionCount(grave, zombie)
    writeRegions(grave, zombie)
    writeGroupedRegions(grave, zombie)
  }

  private fun writeRegionCount(output: DataOutput, zombie: CodeFoldingZombie) {
    val regionCount = zombie.regions.size + zombie.groupedRegions.values.sumOf { it.size }
    writeINT(output, regionCount)
  }

  private fun writeRegions(output: DataOutput, zombie: CodeFoldingZombie) {
    for (region in zombie.regions) {
      writeRegion(output, region)
    }
  }

  private fun writeGroupedRegions(output: DataOutput, zombie: CodeFoldingZombie) {
    for ((_, regions) in zombie.groupedRegions) {
      for (region in regions) {
        writeRegion(output, region)
      }
    }
  }

  private fun writeRegion(output: DataOutput, region: CodeFoldingRegion) {
    writeINT(output, region.startOffset)
    writeINT(output, region.endOffset)
    writeUTF(output, region.placeholderText)
    writeGroupId(output, region.groupId)
    output.writeBoolean(region.neverExpands)
    output.writeBoolean(region.isExpanded)
  }

  private fun writeGroupId(output: DataOutput, groupId: Long?) {
    if (groupId == null) {
      output.writeBoolean(false)
    } else {
      output.writeBoolean(true)
      writeLONG(output, groupId)
    }
  }

  override fun exhumeZombie(grave: DataInput): CodeFoldingZombie {
    val regionCount = readINT(grave)
    val regions = ArrayList<CodeFoldingRegion>()
    val groupedRegions = HashMap<Long, MutableList<CodeFoldingRegion>>()
    repeat(regionCount) {
      val region = read(grave)
      putRegion(region, regions, groupedRegions)
    }
    return CodeFoldingZombie(regions, groupedRegions)
  }

  private fun read(input: DataInput): CodeFoldingRegion {
    val start = readINT(input)
    val end = readINT(input)
    val placeholder = readUTF(input)
    val groupId = readGroupId(input)
    val neverExpands = input.readBoolean()
    val isExpanded = input.readBoolean()
    return CodeFoldingRegion(start, end, placeholder, groupId, neverExpands, isExpanded)
  }

  private fun readGroupId(input: DataInput): Long? {
    return if (input.readBoolean()) {
      readLONG(input)
    } else {
      null
    }
  }
}

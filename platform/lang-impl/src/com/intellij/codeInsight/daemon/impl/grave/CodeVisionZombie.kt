// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.grave

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.CounterCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.ZombieCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.renderers.providers.painter
import com.intellij.codeInsight.daemon.impl.readGutterIcon
import com.intellij.codeInsight.daemon.impl.writeGutterIcon
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.impl.zombie.LimbedZombie
import com.intellij.openapi.util.TextRange
import java.io.DataInput
import java.io.DataOutput
import javax.swing.Icon


internal class CodeVisionZombie(limbs: List<CodeVisionLimb>) : LimbedZombie<CodeVisionLimb>(limbs) {

  fun asCodeVisionEntries(): List<Pair<TextRange, CodeVisionEntry>> {
    return limbs().map { entry ->
      Pair(TextRange(entry.startOffset, entry.endOffset), entry.asEntry())
    }
  }

  internal object Necromancy : LimbedNecromancy<CodeVisionZombie, CodeVisionLimb>(spellLevel=3) {

    override fun buryLimb(grave: DataOutput, limb: CodeVisionLimb) {
      writeInt(grave, limb.startOffset)
      writeInt(grave, limb.endOffset)
      writeString(grave, limb.tooltip)
      writeString(grave, limb.longPresentation)
      writeString(grave, limb.providerId)
      writeGutterIcon(grave, limb.icon)
      writeIntNullable(grave, limb.count)
      writeBool(grave, limb.shouldBeDelimited)
    }

    override fun exhumeLimb(grave: DataInput): CodeVisionLimb {
      val startOffset:           Int = readInt(grave)
      val endOffset:             Int = readInt(grave)
      val tooltip:            String = readString(grave)
      val presentation:       String = readString(grave)
      val providerId:         String = readString(grave)
      val icon:                Icon? = readGutterIcon(grave)
      val count:                Int? = readIntNullable(grave)
      val shouldBeDelimited: Boolean = readBool(grave)
      return CodeVisionLimb(
        startOffset,
        endOffset,
        providerId,
        presentation,
        tooltip,
        icon,
        count,
        shouldBeDelimited,
      )
    }

    override fun formZombie(limbs: List<CodeVisionLimb>): CodeVisionZombie {
      return CodeVisionZombie(limbs)
    }
  }
}

internal data class CodeVisionLimb(
  val startOffset: Int,
  val endOffset: Int,
  val providerId: String,
  val longPresentation: String,
  val tooltip: String,
  val icon: Icon?,
  val count: Int?,
  val shouldBeDelimited: Boolean
) {
  constructor(pair: Pair<TextRange, CodeVisionEntry>) : this(
    pair.first.startOffset,
    pair.first.endOffset,
    pair.second.providerId,
    pair.second.longPresentation,
    pair.second.tooltip,
    pair.second.icon,
    (pair.second as? CounterCodeVisionEntry)?.count,
    pair.second.painter().shouldBeDelimited(pair.second)
  )

  fun asEntry(): ZombieCodeVisionEntry {
    return ZombieCodeVisionEntry(providerId, longPresentation, tooltip, icon, count, shouldBeDelimited = shouldBeDelimited)
  }
}

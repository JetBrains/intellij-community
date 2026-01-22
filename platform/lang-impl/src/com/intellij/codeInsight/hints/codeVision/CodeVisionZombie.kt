// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.CounterCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.ZombieCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.renderers.providers.painter
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.impl.zombie.LimbedZombie
import com.intellij.openapi.util.TextRange
import javax.swing.Icon


internal class CodeVisionZombie private constructor(
  limbs: List<CodeVisionLimb>
) : LimbedZombie<CodeVisionLimb>(limbs) {

  internal object Necromancy : LimbedNecromancy<CodeVisionZombie, CodeVisionLimb>(spellLevel=4) {

    override fun formZombie(limbs: List<CodeVisionLimb>): CodeVisionZombie {
      return CodeVisionZombie(limbs)
    }

    override fun Out.writeLimb(limb: CodeVisionLimb) {
      writeInt(limb.startOffset)
      writeInt(limb.endOffset)
      writeString(limb.tooltip)
      writeString(limb.longPresentation)
      writeString(limb.providerId)
      writeIconOrNull(limb.icon)
      writeIntOrNull(limb.count)
      writeBool(limb.shouldBeDelimited)
    }

    override fun In.readLimb(): CodeVisionLimb {
      val startOffset:           Int = readInt()
      val endOffset:             Int = readInt()
      val tooltip:            String = readString()
      val presentation:       String = readString()
      val providerId:         String = readString()
      val icon:                Icon? = readIconOrNull()
      val count:                Int? = readIntOrNull()
      val shouldBeDelimited: Boolean = readBool()
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

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.grave

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.CounterCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.ZombieCodeVisionEntry
import com.intellij.codeInsight.daemon.impl.readGutterIcon
import com.intellij.codeInsight.daemon.impl.writeGutterIcon
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.impl.zombie.LimbedZombie
import com.intellij.openapi.util.TextRange
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import com.intellij.util.io.IOUtil.readUTF
import com.intellij.util.io.IOUtil.writeUTF
import java.io.DataInput
import java.io.DataOutput
import javax.swing.Icon

internal class CodeVisionZombie(limbs: List<CodeVisionLimb>) : LimbedZombie<CodeVisionLimb>(limbs) {
  fun asCodeVisionEntries(): List<Pair<TextRange, CodeVisionEntry>> {
    return limbs().map { entry ->
      Pair(TextRange(entry.startOffset, entry.endOffset), entry.asEntry())
    }
  }
}

internal object CodeVisionNecromancy : LimbedNecromancy<CodeVisionZombie, CodeVisionLimb>(spellLevel=0) {

  override fun buryLimb(grave: DataOutput, limb: CodeVisionLimb) {
    writeINT(grave, limb.startOffset)
    writeINT(grave, limb.endOffset)
    writeUTF(grave, limb.tooltip)
    writeUTF(grave, limb.longPresentation)
    writeUTF(grave, limb.providerId)
    writeGutterIcon(grave, limb.icon)
    writeCount(grave, limb)
  }

  override fun exhumeLimb(grave: DataInput): CodeVisionLimb {
    val startOffset: Int     = readINT(grave)
    val endOffset: Int       = readINT(grave)
    val tooltip: String      = readUTF(grave)
    val presentation: String = readUTF(grave)
    val provId: String       = readUTF(grave)
    val icon: Icon?          = readGutterIcon(grave)
    val count: Int?          = readCount(grave)
    return CodeVisionLimb(startOffset, endOffset, provId, presentation, tooltip, icon, count)
  }

  override fun formZombie(limbs: List<CodeVisionLimb>): CodeVisionZombie {
    return CodeVisionZombie(limbs)
  }

  private fun writeCount(output: DataOutput, state: CodeVisionLimb) {
    val countExists = state.count != null
    output.writeBoolean(countExists)
    if (countExists) {
      writeINT(output, state.count!!)
    }
  }

  private fun readCount(input: DataInput): Int? {
    val countExists = input.readBoolean()
    return if (countExists) readINT(input) else null
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
) {
  constructor(pair: Pair<TextRange, CodeVisionEntry>) : this(
    pair.first.startOffset,
    pair.first.endOffset,
    pair.second.providerId,
    pair.second.longPresentation,
    pair.second.tooltip,
    pair.second.icon,
    (pair.second as? CounterCodeVisionEntry)?.count,
  )

  fun asEntry(): ZombieCodeVisionEntry {
    return ZombieCodeVisionEntry(providerId, longPresentation, tooltip, icon, count)
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.ParameterHintsPass.HintData
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.impl.zombie.LimbedZombie


private typealias Limb = Pair<Int, HintData>

internal class ParameterHintsZombie private constructor(
  limbs: List<Limb>,
) : LimbedZombie<Limb>(limbs) {

  object Necromancy : LimbedNecromancy<ParameterHintsZombie, Limb>(spellLevel=0) {

    override fun formZombie(limbs: List<Limb>): ParameterHintsZombie {
      return ParameterHintsZombie(limbs)
    }

    override fun Out.writeLimb(limb: Limb) {
      val (offset, hintData) = limb
      writeInt(offset)
      writeHintData(hintData)
    }

    override fun In.readLimb(): Limb {
      val offset:        Int = readInt()
      val hintData: HintData = readHintData()
      return Pair(offset, hintData)
    }

    private fun Out.writeHintData(value: HintData) {
      writeString(value.presentationText)
      writeBool(value.relatesToPrecedingText)
      writeWidthAdjustment(value.widthAdjustment)
    }

    private fun In.readHintData(): HintData {
      val text:                          String = readString()
      val relatesToPrecedingText:       Boolean = readBool()
      val widthAdjustment: HintWidthAdjustment? = readWidthAdjustment()
      return HintData(text, relatesToPrecedingText, widthAdjustment)
    }

    private fun Out.writeWidthAdjustment(widthAdjustment: HintWidthAdjustment?) {
      writeNullable(widthAdjustment) {
        writeString(it.editorTextToMatch)
        writeStringOrNull(it.hintTextToMatch)
        writeInt(it.adjustmentPosition)
      }
    }

    private fun In.readWidthAdjustment(): HintWidthAdjustment? {
      return readNullable {
        val editorTextToMatch: String = readString()
        val hintTextToMatch:  String? = readStringOrNull()
        val adjustmentOffset:     Int = readInt()
        HintWidthAdjustment(editorTextToMatch, hintTextToMatch, adjustmentOffset)
      }
    }
  }
}

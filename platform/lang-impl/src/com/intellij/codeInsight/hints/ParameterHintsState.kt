// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.ParameterHintsPass.HintData
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.impl.zombie.LimbedZombie
import java.io.DataInput
import java.io.DataOutput


private typealias Limb = Pair<Int, HintData>

internal class ParameterHintsZombie(limbs: List<Limb>) : LimbedZombie<Limb>(limbs) {

  object Necromancy : LimbedNecromancy<ParameterHintsZombie, Limb>(spellLevel=0) {

    override fun buryLimb(grave: DataOutput, limb: Limb) {
      val (offset, hintData) = limb
      writeInt(grave, offset)
      writeHintData(grave, hintData)
    }

    override fun exhumeLimb(grave: DataInput): Limb {
      val offset:        Int = readInt(grave)
      val hintData: HintData = readHintData(grave)
      return Pair(offset, hintData)
    }

    override fun formZombie(limbs: List<Limb>): ParameterHintsZombie {
      return ParameterHintsZombie(limbs)
    }

    private fun writeHintData(grave: DataOutput, value: HintData) {
      writeString(grave, value.presentationText)
      writeBool(grave, value.relatesToPrecedingText)
      writeWidthAdjustment(grave, value.widthAdjustment)
    }

    private fun readHintData(grave: DataInput): HintData {
      val text:                          String = readString(grave)
      val relatesToPrecedingText:       Boolean = readBool(grave)
      val widthAdjustment: HintWidthAdjustment? = readWidthAdjustment(grave)
      return HintData(text, relatesToPrecedingText, widthAdjustment)
    }

    private fun writeWidthAdjustment(grave: DataOutput, widthAdjustment: HintWidthAdjustment?) {
      writeNullable(grave, widthAdjustment) {
        writeString(grave, it.editorTextToMatch)
        writeStringNullable(grave, it.hintTextToMatch)
        writeInt(grave, it.adjustmentPosition)
      }
    }

    private fun readWidthAdjustment(grave: DataInput): HintWidthAdjustment? {
      return readNullable(grave) {
        val editorTextToMatch: String = readString(grave)
        val hintTextToMatch:  String? = readStringNullable(grave)
        val adjustmentOffset:     Int = readInt(grave)
        HintWidthAdjustment(editorTextToMatch, hintTextToMatch, adjustmentOffset)
      }
    }
  }
}

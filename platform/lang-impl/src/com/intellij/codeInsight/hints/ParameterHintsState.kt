// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.ParameterHintsPass.HintData
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.impl.zombie.LimbedZombie
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import com.intellij.util.io.IOUtil.readUTF
import com.intellij.util.io.IOUtil.writeUTF
import java.io.DataInput
import java.io.DataOutput

internal class ParameterHintsZombie(limbs: List<Pair<Int, HintData>>) : LimbedZombie<Pair<Int, HintData>>(limbs)

internal object ParameterHintsNecromancy : LimbedNecromancy<ParameterHintsZombie, Pair<Int, HintData>>(spellLevel=0) {

  override fun buryLimb(grave: DataOutput, limb: Pair<Int, HintData>) {
    val (offset, hintData) = limb
    writeINT(grave, offset)
    writeHintData(grave, hintData)
  }

  override fun exhumeLimb(grave: DataInput): Pair<Int, HintData> {
    val offset = readINT(grave)
    val hintData = readHintData(grave)
    return Pair(offset, hintData)
  }

  override fun formZombie(limbs: List<Pair<Int, HintData>>): ParameterHintsZombie {
    return ParameterHintsZombie(limbs)
  }

  private fun readHintData(input: DataInput): HintData {
    val text = readUTF(input)
    val relatesToPrecedingText = input.readBoolean()
    val widthAdjustment = readWidthAdjustment(input)
    return HintData(text, relatesToPrecedingText, widthAdjustment)
  }

  private fun readWidthAdjustment(input: DataInput): HintWidthAdjustment? {
    return if (input.readBoolean()) {
      val editorTextToMatch = readUTF(input)
      val hintTextToMatch = if (input.readBoolean()) readUTF(input) else null
      val adjustmentOffset = readINT(input)
      HintWidthAdjustment(editorTextToMatch, hintTextToMatch, adjustmentOffset)
    } else {
      null
    }
  }

  private fun writeHintData(output: DataOutput, value: HintData) {
    writeUTF(output, value.presentationText)
    output.writeBoolean(value.relatesToPrecedingText)
    writeWidthAdjustment(output, value.widthAdjustment)
  }

  private fun writeWidthAdjustment(output: DataOutput, widthAdjustment: HintWidthAdjustment?) {
    val widthAdjExists = widthAdjustment != null
    output.writeBoolean(widthAdjExists)
    if (widthAdjExists) {
      writeUTF(output, widthAdjustment!!.editorTextToMatch)
      val hintTextToMatch = widthAdjustment.hintTextToMatch
      val hintTextToMatchExists = hintTextToMatch != null
      output.writeBoolean(hintTextToMatchExists)
      if (hintTextToMatchExists) {
        writeUTF(output, hintTextToMatch!!)
      }
      writeINT(output, widthAdjustment.adjustmentPosition)
    }
  }
}

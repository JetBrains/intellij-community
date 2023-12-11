// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.ParameterHintsPass.HintData
import com.intellij.openapi.fileEditor.impl.text.TextEditorCache
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import com.intellij.util.io.IOUtil.readUTF
import com.intellij.util.io.IOUtil.writeUTF
import java.io.DataInput
import java.io.DataOutput

internal data class ParameterHintsState(val contentHash: Int, val hints: List<Pair<Int, HintData>>) {

  object Externalizer : TextEditorCache.ValueExternalizer<ParameterHintsState> {
    override fun serdeVersion() = 0

    override fun save(output: DataOutput, value: ParameterHintsState) {
      writeINT(output, value.contentHash)
      writeINT(output, value.hints.size)
      for ((offset, hintData) in value.hints) {
        writeINT(output, offset)
        writeHintData(output, hintData)
      }
    }

    override fun read(input: DataInput): ParameterHintsState {
      val contentHash = readINT(input)
      val hintCount = readINT(input)
      val hints = ArrayList<Pair<Int, HintData>>(hintCount)
      for (i in 0 until hintCount) {
        val offset = readINT(input)
        val hintData = readHintData(input)
        hints.add(Pair(offset, hintData))
      }
      return ParameterHintsState(contentHash, hints)
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
}

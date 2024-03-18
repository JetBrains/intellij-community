// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.grave

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.CounterCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.ZombieCodeVisionEntry
import com.intellij.codeInsight.daemon.impl.readGutterIcon
import com.intellij.codeInsight.daemon.impl.writeGutterIcon
import com.intellij.openapi.fileEditor.impl.text.VersionedExternalizer
import com.intellij.openapi.util.TextRange
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import com.intellij.util.io.IOUtil.readUTF
import com.intellij.util.io.IOUtil.writeUTF
import java.io.DataInput
import java.io.DataOutput
import javax.swing.Icon

internal data class CodeVisionState(val contentHash: Int, val entries: List<CodeVisionEntryState>) {
  fun asZombies(): List<Pair<TextRange, CodeVisionEntry>> = entries.map { entry ->
    Pair(TextRange(entry.startOffset, entry.endOffset), entry.asZombie())
  }

  object Externalizer : VersionedExternalizer<CodeVisionState> {
    override fun serdeVersion() = 0

    override fun save(output: DataOutput, value: CodeVisionState) {
      writeINT(output, value.contentHash)
      writeINT(output, value.entries.size)
      for (entry in value.entries) {
        writeINT(output, entry.startOffset)
        writeINT(output, entry.endOffset)
        writeUTF(output, entry.tooltip)
        writeUTF(output, entry.longPresentation)
        writeUTF(output, entry.providerId)
        writeGutterIcon(entry.icon, output)
        writeCount(output, entry)
      }
    }

    override fun read(input: DataInput): CodeVisionState {
      val contentHash = readINT(input)
      val entryCount = readINT(input)
      val entries = ArrayList<CodeVisionEntryState>(entryCount)
      repeat(entryCount) {
        val start = readINT(input)
        val end = readINT(input)
        val tooltip = readUTF(input)
        val long = readUTF(input)
        val provId = readUTF(input)
        val icon = readGutterIcon(input)
        val count = readCount(input)
        entries.add(CodeVisionEntryState(start, end, provId, long, tooltip, icon, count))
      }
      return CodeVisionState(contentHash, entries)
    }

    private fun writeCount(output: DataOutput, state: CodeVisionEntryState) {
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
}

internal data class CodeVisionEntryState(
  val startOffset: Int,
  val endOffset: Int,
  val providerId: String,
  val longPresentation: String,
  val tooltip: String,
  val icon: Icon?,
  val count: Int?,
) {
  companion object {
    fun create(pair: Pair<TextRange, CodeVisionEntry>) = CodeVisionEntryState(
      pair.first.startOffset,
      pair.first.endOffset,
      pair.second.providerId,
      pair.second.longPresentation,
      pair.second.tooltip,
      pair.second.icon,
      (pair.second as? CounterCodeVisionEntry)?.count,
    )
  }

  fun asZombie() = ZombieCodeVisionEntry(providerId, longPresentation, tooltip, icon, count)
}

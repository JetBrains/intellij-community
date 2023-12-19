// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.hints.declarative.impl.InlayData
import com.intellij.openapi.fileEditor.impl.text.VersionedExternalizer
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import java.io.DataInput
import java.io.DataOutput

internal class DeclarativeHintsState(val contentHash: Int, val inlayDataList: List<InlayData>) {

  class Externalizer : VersionedExternalizer<DeclarativeHintsState> {

    private val inlayDataExternalizer: InlayData.Externalizer = InlayData.Externalizer()

    companion object {
      // increment on format changed
      private const val SERDE_VERSION = 0
    }

    override fun serdeVersion(): Int = SERDE_VERSION + inlayDataExternalizer.serdeVersion()

    override fun save(output: DataOutput, state: DeclarativeHintsState) {
      writeINT(output, state.contentHash)
      writeINT(output, state.inlayDataList.size)
      for (inlayData in state.inlayDataList) {
        inlayDataExternalizer.save(output, inlayData)
      }
    }

    override fun read(input: DataInput): DeclarativeHintsState {
      val contentHash   = readINT(input)
      val inlayCount    = readINT(input)
      val inlayDataList = buildList(inlayCount) {
        repeat(inlayCount) {
          add(inlayDataExternalizer.read(input))
        }
      }
      return DeclarativeHintsState(contentHash, inlayDataList)
    }
  }
}

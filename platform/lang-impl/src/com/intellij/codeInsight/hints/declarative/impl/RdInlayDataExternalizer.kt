// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayPayload
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import java.io.DataInput
import java.io.DataOutput

@ApiStatus.Internal
object RdInlayDataExternalizer : InlayDataExternalizer(RdPresentationTreeExternalizer) {
  const val RD_INLAY_DATA_SOURCE_ID: String = "rd.deserialized.inlay.data"

  override fun writeProviderClass(output: DataOutput, providerClass: Class<*>) {
    // do nothing
  }

  override fun readProviderClass(input: DataInput): Class<*> {
    return RdDummyInlayProvider::class.java
  }

  override fun writeSourceId(output: DataOutput, sourceId: String) {
    // do nothing
  }

  override fun readSourceId(input: DataInput): String {
    return RD_INLAY_DATA_SOURCE_ID
  }

  override fun writePayloads(output: DataOutput, payloads: List<InlayPayload>?) {
    // do nothing
  }

  override fun readPayloads(input: DataInput): List<InlayPayload>? {
    return null
  }
}

private object RdPresentationTreeExternalizer : PresentationTreeExternalizer() {
  override fun writeInlayActionPayload(output: DataOutput, actionPayload: InlayActionPayload) {
    error("InlayActionPayload should not be sent to frontend")
  }

  override fun readInlayActionPayload(input: DataInput): InlayActionPayload {
    error("InlayActionPayload should not be read on frontend")
  }

  override fun readInlayActionData(input: DataInput): InlayActionData {
    return INLAY_ACTION_DATA_PLACEHOLDER
  }

  override fun writeInlayActionData(output: DataOutput, inlayActionData: InlayActionData) {
    // do nothing
  }
}

private object RdInlayActionPayloadPlaceholder : InlayActionPayload

private val INLAY_ACTION_DATA_PLACEHOLDER = InlayActionData(RdInlayActionPayloadPlaceholder, "rd.placeholder.handler.id")

private object RdDummyInlayProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
    error("Dummy provider must not be called")
  }
}

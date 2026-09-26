// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import com.intellij.util.io.IOUtil.readUTF
import com.intellij.util.io.IOUtil.writeUTF
import org.jetbrains.annotations.ApiStatus
import java.io.DataInput
import java.io.DataOutput

@ApiStatus.Internal
abstract class PresentationTreeExternalizer : TinyTree.Externalizer<Any?>() {

  companion object {
    // increment on format changed
    private const val SERDE_VERSION = 2
  }

  override fun serdeVersion(): Int = SERDE_VERSION + super.serdeVersion()

  override fun writeDataPayload(output: DataOutput, payload: Any?) {
    when (payload) {
      is String? -> {
        writeINT(output, 0)
        writeNullableString(output, payload)
      }
      is ActionWithContent -> {
        writeINT(output, 1)
        writeUTF(output, payload.content as String)
        writeInlayActionData(output, payload.actionData)
      }
      is InlayActionData -> {
        writeINT(output, 2)
        writeInlayActionData(output, payload)
      }
      else -> throw IllegalArgumentException("unknown data payload type: $payload")
    }
  }

  override fun readDataPayload(input: DataInput): Any? {
    val type = readINT(input)
    when (type) {
      0 -> {
        val string = readNullableString(input) ?: return null
        return decorateContent(string)
      }
      1 -> {
        val content = decorateContent(readUTF(input))
        val inlayActionData = readInlayActionData(input) ?: return content
        return ActionWithContent(inlayActionData, content)
      }
      2 -> {
        return readInlayActionData(input)
      }
      else -> throw IllegalStateException("unknown data payload type: $type")
    }
  }

  open fun writeInlayActionData(output: DataOutput, inlayActionData: InlayActionData) {
    writeNamedInlayActionPayload(output, inlayActionData.handlerId, inlayActionData.payload)
  }

  open fun readInlayActionData(input: DataInput): InlayActionData? =
    readNamedInlayActionPayload(input) { n, p -> InlayActionData(p, n) }

  abstract fun writeInlayActionPayload(output: DataOutput, actionPayload: InlayActionPayload)

  abstract fun readInlayActionPayload(input: DataInput): InlayActionPayload

  protected open fun decorateContent(content: String): String = content
}

internal fun writeNullableString(output: DataOutput, value: String?) {
  if (value == null) {
    output.writeBoolean(false)
  } else {
    output.writeBoolean(true)
    writeUTF(output, value)
  }
}

internal fun readNullableString(input: DataInput): String? {
  return if (input.readBoolean()) {
    readUTF(input)
  } else {
    null
  }
}

internal fun PresentationTreeExternalizer.writeNamedInlayActionPayload(
  output: DataOutput,
  name: String,
  payload: InlayActionPayload,
) {
  if (payload is NonPersistableInlayActionPayload) {
    writeNullableString(output, null)
  }
  else {
    writeNullableString(output, name)
    writeInlayActionPayload(output, payload)
  }
}

internal inline fun <T> PresentationTreeExternalizer.readNamedInlayActionPayload(
  input: DataInput,
  constructor: (String, InlayActionPayload) -> T,
): T? {
  val name = readNullableString(input) ?: return null
  val payload = readInlayActionPayload(input)
  return constructor(name, payload)
}

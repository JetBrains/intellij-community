// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree
import com.intellij.openapi.util.registry.Registry
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
    private const val SERDE_VERSION = 1
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
        return ActionWithContent(readInlayActionData(input), content)
      }
      2 -> {
        return readInlayActionData(input)
      }
      else -> throw IllegalStateException("unknown data payload type: $type")
    }
  }

  private fun writeNullableString(output: DataOutput, value: String?) {
    if (value == null) {
      output.writeBoolean(false)
    } else {
      output.writeBoolean(true)
      writeUTF(output, value)
    }
  }

  private fun readNullableString(input: DataInput): String? {
    return if (input.readBoolean()) {
      readUTF(input)
    } else {
      null
    }
  }

  open fun writeInlayActionData(output: DataOutput, inlayActionData: InlayActionData) {
    writeUTF(output, inlayActionData.handlerId)
    writeInlayActionPayload(output, inlayActionData.payload)
  }

  open fun readInlayActionData(input: DataInput): InlayActionData {
    val handlerId = readUTF(input)
    val payload = readInlayActionPayload(input)
    return InlayActionData(payload, handlerId)
  }

  abstract fun writeInlayActionPayload(output: DataOutput, actionPayload: InlayActionPayload)

  abstract fun readInlayActionPayload(input: DataInput): InlayActionPayload

  protected open fun decorateContent(content: String): String = content
}

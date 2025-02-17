// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiFile
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import com.intellij.util.io.IOUtil.readUTF
import com.intellij.util.io.IOUtil.writeUTF
import org.jetbrains.annotations.ApiStatus
import java.io.DataInput
import java.io.DataOutput

/**
 * Describes completely a single inlay hint from some [InlayHintsProvider].
 *
 * Note that an instance of this class does not necessarily map one-to-one to an [com.intellij.openapi.editor.Inlay]
 * (see [DeclarativeInlayRendererBase.toInlayData][com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase.toInlayData]).
 * This is mainly due to interline inlays
 * (e.g. [AboveLineIndentedPosition][com.intellij.codeInsight.hints.declarative.AboveLineIndentedPosition]),
 * where, to display multiple inlay hints on a single interline,
 * a single [com.intellij.openapi.editor.InlayModel.addBlockElement] must be used.
 */
@ApiStatus.Internal
data class InlayData(
  val position: InlayPosition,
  @NlsContexts.HintText val tooltip: String?,
  val hintFormat: HintFormat,
  /**
   * Constraints:
   *
   * [TinyTree.getDataPayload] returns one of:
   * - [String]
   * - [com.intellij.codeInsight.hints.declarative.InlayActionData]
   * - [com.intellij.codeInsight.hints.declarative.impl.ActionWithContent]
   * - null
   *
   * [TinyTree.getBytePayload] returns one of the tags defined in [com.intellij.codeInsight.hints.declarative.impl.InlayTags]
   */
  val tree: TinyTree<Any?>,
  val providerId: String,
  /** Strikethrough text. Used in settings to indicate disabled inlay hints. */
  val disabled: Boolean, // todo: make this a formatting option
  /**
   * Payloads available to inlay actions available in the right-click popup menu (via "InlayMenu" action group)
   *
   * @see DeclarativeInlayActionService
   */
  val payloads: List<InlayPayload>?,
  val providerClass: Class<*>, // Just for debugging purposes
  val sourceId: String,
)

internal object InlayDataExternalizer : DataExternalizer<InlayData> {
  // increment on format changed
  private const val SERDE_VERSION = 8

  private val treeExternalizer: PresentationTreeExternalizer = PresentationTreeExternalizer

  fun serdeVersion(): Int = SERDE_VERSION + treeExternalizer.serdeVersion()

  override fun save(output: DataOutput, inlayData: InlayData) {
    writePosition(output, inlayData.position)
    writeTooltip(output, inlayData.tooltip)
    writeHintFormat(output, inlayData.hintFormat)
    treeExternalizer.save(output, inlayData.tree)
    writeUTF(output, inlayData.providerId)
    output.writeBoolean(inlayData.disabled)
    writePayloads(output, inlayData.payloads)
    writeSourceId(output, inlayData.sourceId)
  }

  override fun read(input: DataInput): InlayData {
    val position: InlayPosition       = readPosition(input)
    val tooltip: String?              = readTooltip(input)
    val hintFormat: HintFormat        = readHintFormat(input)
    val tree: TinyTree<Any?>          = treeExternalizer.read(input)
    val providerId: String            = readUTF(input)
    val disabled: Boolean             = input.readBoolean()
    val payloads: List<InlayPayload>? = readPayloads(input)
    val providerClass: Class<*>       = ZombieInlayHintsProvider::class.java
    val sourceId: String              = readSourceId(input)
    return InlayData(position, tooltip, hintFormat, tree, providerId, disabled, payloads, providerClass, sourceId)
  }

  private fun writePosition(output: DataOutput, position: InlayPosition) {
    when (position) {
      is InlineInlayPosition -> {
        writeINT(output, 0)
        writeINT(output, position.offset)
        output.writeBoolean(position.relatedToPrevious)
        writeINT(output, position.priority)
      }
      is EndOfLinePosition -> {
        writeINT(output, 1)
        writeINT(output, position.line)
        writeINT(output, position.priority)
      }
      is AboveLineIndentedPosition -> {
        writeINT(output, 2)
        writeINT(output, position.offset)
        writeINT(output, position.verticalPriority)
        writeINT(output, position.priority)
      }
    }
  }

  private fun readPosition(input: DataInput): InlayPosition {
    val type = readINT(input)
    if (type == 0) {
      val offset = readINT(input)
      val related = input.readBoolean()
      val priority = readINT(input)
      return InlineInlayPosition(offset, related, priority)
    } else if (type == 1) {
      val line = readINT(input)
      val priority = readINT(input)
      return EndOfLinePosition(line, priority)
    } else if (type == 2) {
      val offset = readINT(input)
      val verticalPriority = readINT(input)
      val priority = readINT(input)
      return AboveLineIndentedPosition(offset, verticalPriority, priority)
    }
    throw IllegalStateException("unknown inlay position type: $type")
  }

  private fun writeTooltip(output: DataOutput, tooltip: String?) {
    if (tooltip == null) {
      output.writeBoolean(false)
    } else {
      output.writeBoolean(true)
      writeUTF(output, tooltip)
    }
  }

  private fun readTooltip(input: DataInput): String? {
    return if (input.readBoolean()) {
      readUTF(input)
    } else {
      null
    }
  }

  private fun writeHintFormat(output: DataOutput, hintFormat: HintFormat) {
    writeUTF(output, hintFormat.colorKind.name)
    writeUTF(output, hintFormat.fontSize.name)
    writeUTF(output, hintFormat.horizontalMarginPadding.name)
  }

  private fun readHintFormat(input: DataInput): HintFormat {
    val hintColorKind= HintColorKind.valueOf(readUTF(input))
    val hintFontSize= HintFontSize.valueOf(readUTF(input))
    val padding= HintMarginPadding.valueOf(readUTF(input))
    return HintFormat(hintColorKind, hintFontSize, padding)
  }

  private fun writePayloads(output: DataOutput, payloads: List<InlayPayload>?) {
    if (payloads == null) {
      output.writeBoolean(false)
    } else {
      output.writeBoolean(true)
      writeINT(output, payloads.size)
      for (p in payloads) {
        writeInlayPayload(output, p)
      }
    }
  }

  private fun readPayloads(input: DataInput): List<InlayPayload>? {
    if (input.readBoolean()) {
      val payloadCount = readINT(input)
      val payloads = ArrayList<InlayPayload>(payloadCount)
      repeat(payloadCount) {
        val inlayPayload = readInlayPayload(input)
        payloads.add(inlayPayload)
      }
      return payloads
    } else {
      return null
    }
  }

  private fun writeInlayPayload(output: DataOutput, payload: InlayPayload) {
    writeUTF(output, payload.payloadName)
    treeExternalizer.writeInlayActionPayload(output, payload.payload)
  }

  private fun readInlayPayload(input: DataInput): InlayPayload {
    val payloadName = readUTF(input)
    val inlayActionPayload = treeExternalizer.readInlayActionPayload(input)
    return InlayPayload(payloadName, inlayActionPayload)
  }

  private fun writeSourceId(output: DataOutput, sourceId: String) {
    writeUTF(output, sourceId)
  }

  private fun readSourceId(input: DataInput): String {
    return readUTF(input)
  }
}

private class ZombieInlayHintsProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
    throw UnsupportedOperationException("Zombie provider does not support inlay collecting")
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.formatting.visualLayer.VisualFormattingLayerElement.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.impl.zombie.LimbedZombie
import com.intellij.util.concurrency.annotations.RequiresEdt


internal sealed class VisualFormattingLimb {

  abstract fun toElement(): VisualFormattingLayerElement

  data class InlineLimb(val offset: Int, val length: Int) : VisualFormattingLimb() {
    override fun toElement(): VisualFormattingLayerElement = InlineInlay(offset, length)
  }

  data class BlockLimb(val offset: Int, val lines: Int) : VisualFormattingLimb() {
    override fun toElement(): VisualFormattingLayerElement = BlockInlay(offset, lines)
  }

  data class FoldingLimb(val offset: Int, val length: Int) : VisualFormattingLimb() {
    override fun toElement(): VisualFormattingLayerElement = Folding(offset, length)
  }

  companion object {
    fun fromElement(element: VisualFormattingLayerElement): VisualFormattingLimb = when (element) {
      is InlineInlay -> InlineLimb(element.offset, element.length)
      is BlockInlay -> BlockLimb(element.offset, element.lines)
      is Folding -> FoldingLimb(element.offset, element.length)
    }
  }
}

internal class VisualFormattingZombie private constructor(
  limbs: List<VisualFormattingLimb>,
) : LimbedZombie<VisualFormattingLimb>(limbs) {

  @RequiresEdt
  fun applyState(editor: Editor) {
    val elements = limbs().map { it.toElement() }
    VisualFormattingLayerService.getInstance()
      .applyVisualFormattingLayerElementsToEditor(editor, elements)
  }

  object Necromancy : LimbedNecromancy<VisualFormattingZombie, VisualFormattingLimb>(spellLevel = 0) {

    override fun formZombie(limbs: List<VisualFormattingLimb>): VisualFormattingZombie {
      return VisualFormattingZombie(limbs)
    }

    override fun Out.writeLimb(limb: VisualFormattingLimb) {
      when (limb) {
        is VisualFormattingLimb.InlineLimb -> {
          writeInt(0)
          writeInt(limb.offset)
          writeInt(limb.length)
        }
        is VisualFormattingLimb.BlockLimb -> {
          writeInt(1)
          writeInt(limb.offset)
          writeInt(limb.lines)
        }
        is VisualFormattingLimb.FoldingLimb -> {
          writeInt(2)
          writeInt(limb.offset)
          writeInt(limb.length)
        }
      }
    }

    override fun In.readLimb(): VisualFormattingLimb {
      val type = readInt()
      return when (type) {
        0 -> {
          val offset = readInt()
          val length = readInt()
          VisualFormattingLimb.InlineLimb(offset, length)
        }
        1 -> {
          val offset = readInt()
          val lines = readInt()
          VisualFormattingLimb.BlockLimb(offset, lines)
        }
        2 -> {
          val offset = readInt()
          val length = readInt()
          VisualFormattingLimb.FoldingLimb(offset, length)
        }
        else -> error("Unknown visual formatting limb type: $type")
      }
    }
  }
}

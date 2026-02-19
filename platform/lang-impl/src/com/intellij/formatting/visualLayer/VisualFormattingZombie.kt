// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.impl.zombie.LimbedZombie


internal class VisualFormattingZombie private constructor(
  limbs: List<Limb>,
) : LimbedZombie<VisualFormattingZombie.Limb>(limbs) {

  class Limb(
    val type: Type,
    val offset: Int,
    val length: Int,
  ) {

    @Suppress("FunctionName")
    companion object {
      fun Inline(offset: Int, length: Int) = Limb(Type.INLINE, offset, length)
      fun Block(offset: Int, length: Int) = Limb(Type.BLOCK, offset, length)
      fun Folding(offset: Int, length: Int) = Limb(Type.FOLDING, offset, length)
    }

    fun toElement(): VisualFormattingLayerElement {
      return when (type) {
        Type.INLINE -> VisualFormattingLayerElement.InlineInlay(offset, length)
        Type.BLOCK -> VisualFormattingLayerElement.BlockInlay(offset, length)
        Type.FOLDING -> VisualFormattingLayerElement.Folding(offset, length)
      }
    }

    enum class Type {
      INLINE,
      BLOCK,
      FOLDING,
    }
  }

  object Necromancy : LimbedNecromancy<VisualFormattingZombie, Limb>(spellLevel=0) {

    override fun formZombie(limbs: List<Limb>): VisualFormattingZombie {
      return VisualFormattingZombie(limbs)
    }

    override fun Out.writeLimb(limb: Limb) {
      writeEnum(limb.type)
      writeInt(limb.offset)
      writeInt(limb.length)
    }

    override fun In.readLimb(): Limb {
      val type:  Limb.Type = readEnum()
      val offset:      Int = readInt()
      val length:      Int = readInt()
      return Limb(type, offset, length)
    }
  }
}

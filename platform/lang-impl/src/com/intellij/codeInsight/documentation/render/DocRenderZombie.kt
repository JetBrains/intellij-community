// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.codeInsight.documentation.render.DocRenderZombie.Limb
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.impl.zombie.LimbedZombie


internal class DocRenderZombie private constructor(
  limbs: List<Limb>,
) : LimbedZombie<Limb>(limbs) {

  data class Limb(
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
  )

  object Necromancy : LimbedNecromancy<DocRenderZombie, Limb>(spellLevel=0) {

    override fun formZombie(limbs: List<Limb>): DocRenderZombie {
      return DocRenderZombie(limbs)
    }

    override fun Out.writeLimb(limb: Limb) {
      writeInt(limb.startOffset)
      writeInt(limb.endOffset)
      writeString(limb.text)
    }

    override fun In.readLimb(): Limb {
      val startOffset: Int = readInt()
      val endOffset:   Int = readInt()
      val text:     String = readString()
      return Limb(startOffset, endOffset, text)
    }
  }
}

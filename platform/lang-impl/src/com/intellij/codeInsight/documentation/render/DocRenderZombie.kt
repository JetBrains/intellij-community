// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.codeInsight.documentation.render.DocRenderZombie.Limb
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.impl.zombie.LimbedZombie
import java.io.DataInput
import java.io.DataOutput


internal class DocRenderZombie(limbs: List<Limb>) : LimbedZombie<Limb>(limbs) {

  data class Limb(
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
  )

  object Necromancy : LimbedNecromancy<DocRenderZombie, Limb>(spellLevel=0) {
    override fun buryLimb(grave: DataOutput, limb: Limb) {
      writeInt(grave, limb.startOffset)
      writeInt(grave, limb.endOffset)
      writeString(grave, limb.text)
    }

    override fun exhumeLimb(grave: DataInput): Limb {
      val startOffset: Int = readInt(grave)
      val endOffset:   Int = readInt(grave)
      val text:     String = readString(grave)
      return Limb(startOffset, endOffset, text)
    }

    override fun formZombie(limbs: List<Limb>): DocRenderZombie {
      return DocRenderZombie(limbs)
    }
  }
}

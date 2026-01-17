// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.indentGuide

import com.intellij.openapi.editor.IndentGuideDescriptor
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.impl.zombie.LimbedZombie
import java.io.DataInput
import java.io.DataOutput


internal class IndentGuideZombie(
  limbs: List<IndentGuideDescriptor>,
) : LimbedZombie<IndentGuideDescriptor>(limbs) {

  object Necromancy : LimbedNecromancy<IndentGuideZombie, IndentGuideDescriptor>(spellLevel=0) {
    override fun buryLimb(grave: DataOutput, limb: IndentGuideDescriptor) {
      writeInt(grave, limb.indentLevel)
      writeInt(grave, limb.codeConstructStartLine)
      writeInt(grave, limb.startLine)
      writeInt(grave, limb.endLine)
    }

    override fun exhumeLimb(grave: DataInput): IndentGuideDescriptor {
      val indentLevel:            Int = readInt(grave)
      val codeConstructStartLine: Int = readInt(grave)
      val startLine:              Int = readInt(grave)
      val endLine:                Int = readInt(grave)
      return IndentGuideDescriptor(
        indentLevel,
        codeConstructStartLine,
        startLine,
        endLine,
      )
    }

    override fun formZombie(limbs: List<IndentGuideDescriptor>): IndentGuideZombie {
      return IndentGuideZombie(limbs)
    }
  }
}

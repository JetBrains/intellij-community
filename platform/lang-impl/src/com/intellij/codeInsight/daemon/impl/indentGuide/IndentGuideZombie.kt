// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.indentGuide

import com.intellij.openapi.editor.IndentGuideDescriptor
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.impl.zombie.LimbedZombie


internal class IndentGuideZombie private constructor(
  limbs: List<IndentGuideDescriptor>,
) : LimbedZombie<IndentGuideDescriptor>(limbs) {

  object Necromancy : LimbedNecromancy<IndentGuideZombie, IndentGuideDescriptor>(spellLevel=0) {

    override fun formZombie(limbs: List<IndentGuideDescriptor>): IndentGuideZombie {
      return IndentGuideZombie(limbs)
    }

    override fun Out.writeLimb(limb: IndentGuideDescriptor) {
      writeInt(limb.indentLevel)
      writeInt(limb.codeConstructStartLine)
      writeInt(limb.startLine)
      writeInt(limb.endLine)
    }

    override fun In.readLimb(): IndentGuideDescriptor {
      val indentLevel:            Int = readInt()
      val codeConstructStartLine: Int = readInt()
      val startLine:              Int = readInt()
      val endLine:                Int = readInt()
      return IndentGuideDescriptor(
        indentLevel,
        codeConstructStartLine,
        startLine,
        endLine,
      )
    }
  }
}

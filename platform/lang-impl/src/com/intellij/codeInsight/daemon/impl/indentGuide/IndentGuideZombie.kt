// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.indentGuide

import com.intellij.openapi.editor.IndentGuideDescriptor
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.impl.zombie.LimbedZombie
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import java.io.DataInput
import java.io.DataOutput


internal class IndentGuideZombie(
  limbs: List<IndentGuideDescriptor>,
) : LimbedZombie<IndentGuideDescriptor>(limbs) {

  object Necromancy : LimbedNecromancy<IndentGuideZombie, IndentGuideDescriptor>(spellLevel=0) {
    override fun buryLimb(grave: DataOutput, limb: IndentGuideDescriptor) {
      writeINT(grave, limb.indentLevel)
      writeINT(grave, limb.codeConstructStartLine)
      writeINT(grave, limb.startLine)
      writeINT(grave, limb.endLine)
    }

    override fun exhumeLimb(grave: DataInput): IndentGuideDescriptor {
      val indentLevel           : Int = readINT(grave)
      val codeConstructStartLine: Int = readINT(grave)
      val startLine             : Int = readINT(grave)
      val endLine               : Int = readINT(grave)
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

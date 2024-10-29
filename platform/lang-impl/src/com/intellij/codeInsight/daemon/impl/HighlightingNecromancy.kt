// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import com.intellij.util.io.IOUtil.readUTF
import com.intellij.util.io.IOUtil.writeUTF
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.DataInput
import java.io.DataOutput
import javax.swing.Icon

@Internal
object HighlightingNecromancy : LimbedNecromancy<HighlightingZombie, HighlightingLimb>(spellLevel=0, isDeepBury=true) {

  override fun buryLimb(grave: DataOutput, limb: HighlightingLimb) {
    writeINT(grave, limb.startOffset)
    writeINT(grave, limb.endOffset)
    writeINT(grave, limb.layer)
    writeINT(grave, limb.targetArea.ordinal)
    writeTextAttributesKey(grave, limb.textAttributesKey)
    writeTextAttributes(grave, limb.textAttributes)
    writeGutterIcon(grave, limb.gutterIcon)
  }

  override fun exhumeLimb(grave: DataInput): HighlightingLimb {
    val startOffset: Int              = readINT(grave)
    val endOffset: Int                = readINT(grave)
    val layer: Int                    = readINT(grave)
    val target: HighlighterTargetArea = readTargetArea(grave)
    val key: TextAttributesKey?       = readTextAttributesKey(grave)
    val attributes: TextAttributes?   = readTextAttributes(grave)
    val icon: Icon?                   = readGutterIcon(grave)
    return HighlightingLimb(startOffset, endOffset, layer, target, key, attributes, icon)
  }

  override fun formZombie(limbs: List<HighlightingLimb>): HighlightingZombie {
    return HighlightingZombie(limbs)
  }

  private fun writeTextAttributesKey(output: DataOutput, textAttributesKey: TextAttributesKey?) {
    if (textAttributesKey == null) {
      output.writeBoolean(false)
    } else {
      output.writeBoolean(true)
      writeUTF(output, textAttributesKey.externalName)
    }
  }

  private fun writeTextAttributes(output: DataOutput, textAttributes: TextAttributes?) {
    if (textAttributes == null) {
      output.writeBoolean(false)
    } else {
      output.writeBoolean(true)
      textAttributes.writeExternal(output)
    }
  }

  private fun readTargetArea(input: DataInput): HighlighterTargetArea {
    val index = readINT(input)
    return HighlighterTargetArea.entries[index]
  }

  private fun readTextAttributesKey(input: DataInput): TextAttributesKey? {
    return if (input.readBoolean()) {
      TextAttributesKey.find(readUTF(input))
    } else {
      null
    }
  }

  private fun readTextAttributes(input: DataInput): TextAttributes? {
    return if (input.readBoolean()) {
      TextAttributes(input)
    } else {
      null
    }
  }
}

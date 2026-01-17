// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.DataInput
import java.io.DataOutput
import javax.swing.Icon


@Internal
object HighlightingNecromancy : LimbedNecromancy<HighlightingZombie, HighlightingLimb>(spellLevel=0, isDeepBury=true) {

  override fun buryLimb(grave: DataOutput, limb: HighlightingLimb) {
    writeInt(grave, limb.startOffset)
    writeInt(grave, limb.endOffset)
    writeInt(grave, limb.layer)
    writeInt(grave, limb.targetArea.ordinal)
    writeTextAttributesKey(grave, limb.textAttributesKey)
    writeTextAttributes(grave, limb.textAttributes)
    writeGutterIcon(grave, limb.gutterIcon)
  }

  override fun exhumeLimb(grave: DataInput): HighlightingLimb {
    val startOffset: Int              = readInt(grave)
    val endOffset: Int                = readInt(grave)
    val layer: Int                    = readInt(grave)
    val target: HighlighterTargetArea = readTargetArea(grave)
    val key: TextAttributesKey?       = readTextAttributesKey(grave)
    val attributes: TextAttributes?   = readTextAttributes(grave)
    val icon: Icon?                   = readGutterIcon(grave)
    return HighlightingLimb(
      startOffset,
      endOffset,
      layer,
      target,
      key,
      attributes,
      icon,
    )
  }

  override fun formZombie(limbs: List<HighlightingLimb>): HighlightingZombie {
    return HighlightingZombie(limbs)
  }

  private fun writeTextAttributesKey(grave: DataOutput, textAttributesKey: TextAttributesKey?) {
    writeStringNullable(grave, textAttributesKey?.externalName)
  }

  private fun writeTextAttributes(grave: DataOutput, textAttributes: TextAttributes?) {
    writeNullable(grave, textAttributes) { textAttributes ->
      textAttributes.writeExternal(grave)
    }
  }

  private fun readTargetArea(grave: DataInput): HighlighterTargetArea {
    val index = readInt(grave)
    return HighlighterTargetArea.entries[index]
  }

  private fun readTextAttributesKey(grave: DataInput): TextAttributesKey? {
    return readStringNullable(grave)?.let {
      TextAttributesKey.find(it)
    }
  }

  private fun readTextAttributes(grave: DataInput): TextAttributes? {
    return readNullable(grave) {
      TextAttributes(grave)
    }
  }
}

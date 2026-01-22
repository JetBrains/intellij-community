// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.Icon


@Internal
object HighlightingNecromancy : LimbedNecromancy<HighlightingZombie, HighlightingLimb>(spellLevel=1, isDeepBury=true) {

  override fun Out.writeLimb(limb: HighlightingLimb) {
    writeInt(limb.startOffset)
    writeInt(limb.endOffset)
    writeInt(limb.layer)
    writeInt(limb.targetArea.ordinal)
    writeTextAttributesKey(limb.textAttributesKey)
    writeTextAttributes(limb.textAttributes)
    writeIconOrNull(limb.gutterIcon)
  }

  override fun In.readLimb(): HighlightingLimb {
    val startOffset: Int              = readInt()
    val endOffset: Int                = readInt()
    val layer: Int                    = readInt()
    val target: HighlighterTargetArea = readTargetArea()
    val key: TextAttributesKey?       = readTextAttributesKey()
    val attributes: TextAttributes?   = readTextAttributes()
    val icon: Icon?                   = readIconOrNull()
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

  private fun Out.writeTextAttributesKey(textAttributesKey: TextAttributesKey?) {
    writeStringOrNull(textAttributesKey?.externalName)
  }

  private fun Out.writeTextAttributes(textAttributes: TextAttributes?) {
    writeNullable(textAttributes) { textAttributes ->
      textAttributes.writeExternal(output)
    }
  }

  private fun In.readTargetArea(): HighlighterTargetArea {
    val index = readInt()
    return HighlighterTargetArea.entries[index]
  }

  private fun In.readTextAttributesKey(): TextAttributesKey? {
    return readStringOrNull()?.let {
      TextAttributesKey.find(it)
    }
  }

  private fun In.readTextAttributes(): TextAttributes? {
    return readNullable {
      TextAttributes(input)
    }
  }
}

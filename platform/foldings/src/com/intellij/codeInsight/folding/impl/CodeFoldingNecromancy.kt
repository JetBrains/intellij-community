// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl

import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy


internal object CodeFoldingNecromancy : LimbedNecromancy<CodeFoldingZombie, FoldLimb>(spellLevel=3) {

  override fun formZombie(limbs: List<FoldLimb>): CodeFoldingZombie {
    return CodeFoldingZombie(limbs)
  }

  override fun Out.writeLimb(limb: FoldLimb) {
    writeInt(limb.startOffset)
    writeInt(limb.endOffset)
    writeString(limb.placeholderText)
    writeLongOrNull(limb.groupId)
    writeBool(limb.neverExpands)
    writeBool(limb.isExpanded)
    writeBool(limb.isCollapsedByDefault)
    writeBool(limb.isFrontendCreated)
  }

  override fun In.readLimb(): FoldLimb {
    val startOffset:              Int = readInt()
    val endOffset:                Int = readInt()
    val placeholderText:       String = readString()
    val groupId:                Long? = readLongOrNull()
    val neverExpands:         Boolean = readBool()
    val isExpanded:           Boolean = readBool()
    val isCollapsedByDefault: Boolean = readBool()
    val isFrontendCreated:    Boolean = readBool()
    return FoldLimb(
      startOffset,
      endOffset,
      placeholderText,
      groupId,
      neverExpands,
      isExpanded,
      isCollapsedByDefault,
      isFrontendCreated,
    )
  }
}

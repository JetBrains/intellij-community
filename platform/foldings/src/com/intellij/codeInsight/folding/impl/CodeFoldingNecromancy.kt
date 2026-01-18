// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl

import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import java.io.DataInput
import java.io.DataOutput


internal object CodeFoldingNecromancy : LimbedNecromancy<CodeFoldingZombie, FoldLimb>(spellLevel=3) {

  override fun formZombie(limbs: List<FoldLimb>): CodeFoldingZombie {
    return CodeFoldingZombie(limbs)
  }

  override fun buryLimb(grave: DataOutput, limb: FoldLimb) {
    writeInt(grave, limb.startOffset)
    writeInt(grave, limb.endOffset)
    writeString(grave, limb.placeholderText)
    writeLongNullable(grave, limb.groupId)
    writeBool(grave, limb.neverExpands)
    writeBool(grave, limb.isExpanded)
    writeBool(grave, limb.isCollapsedByDefault)
    writeBool(grave, limb.isFrontendCreated)
  }

  override fun exhumeLimb(grave: DataInput): FoldLimb {
    val startOffset:              Int = readInt(grave)
    val endOffset:                Int = readInt(grave)
    val placeholderText:       String = readString(grave)
    val groupId:                Long? = readLongNullable(grave)
    val neverExpands:         Boolean = readBool(grave)
    val isExpanded:           Boolean = readBool(grave)
    val isCollapsedByDefault: Boolean = readBool(grave)
    val isFrontendCreated:    Boolean = readBool(grave)
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

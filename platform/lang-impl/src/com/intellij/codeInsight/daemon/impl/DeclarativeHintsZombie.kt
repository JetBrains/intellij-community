// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.hints.declarative.impl.InlayData
import com.intellij.codeInsight.hints.declarative.impl.InlayDataExternalizer
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.impl.zombie.LimbedZombie
import java.io.*

internal class DeclarativeHintsZombie(limbs: List<InlayData>) : LimbedZombie<InlayData>(limbs)

internal object DeclarativeHintsNecromancy : LimbedNecromancy<DeclarativeHintsZombie, InlayData>(InlayDataExternalizer.serdeVersion()) {

  override fun buryLimb(grave: DataOutput, limb: InlayData) {
    InlayDataExternalizer.save(grave, limb)
  }

  override fun exhumeLimb(grave: DataInput): InlayData {
    return InlayDataExternalizer.read(grave)
  }

  override fun formZombie(limbs: List<InlayData>): DeclarativeHintsZombie {
    return DeclarativeHintsZombie(limbs)
  }
}

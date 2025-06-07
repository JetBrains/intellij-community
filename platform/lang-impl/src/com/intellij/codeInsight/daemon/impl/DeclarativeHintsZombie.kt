// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.zombie.LimbedNecromancy
import com.intellij.openapi.editor.impl.zombie.LimbedZombie
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import com.intellij.util.io.DataInputOutputUtil.readINT
import com.intellij.util.io.DataInputOutputUtil.writeINT
import com.intellij.util.io.IOUtil.readUTF
import com.intellij.util.io.IOUtil.writeUTF
import java.io.DataInput
import java.io.DataOutput

internal class DeclarativeHintsZombie(limbs: List<InlayData>) : LimbedZombie<InlayData>(limbs)

internal object DeclarativeHintsNecromancy
  : LimbedNecromancy<DeclarativeHintsZombie, InlayData>(NecromancyInlayDataExternalizer.serdeVersion()) {

  override fun buryLimb(grave: DataOutput, limb: InlayData) {
    NecromancyInlayDataExternalizer.save(grave, limb)
  }

  override fun exhumeLimb(grave: DataInput): InlayData {
    return NecromancyInlayDataExternalizer.read(grave)
  }

  override fun formZombie(limbs: List<InlayData>): DeclarativeHintsZombie {
    return DeclarativeHintsZombie(limbs)
  }
}

private object NecromancyPresentationTreeExternalizer : PresentationTreeExternalizer() {
  private const val SERDE_VERSION = 0
  override fun serdeVersion(): Int = SERDE_VERSION + super.serdeVersion()

  override fun writeInlayActionPayload(output: DataOutput, actionPayload: InlayActionPayload) {
    when(actionPayload) {
      is StringInlayActionPayload -> {
        writeINT(output, 0)
        writeUTF(output, actionPayload.text)
      }
      is PsiPointerInlayActionPayload -> {
        writeINT(output, 1)
      }
      is SymbolPointerInlayActionPayload -> {
        writeINT(output, 2)
      }
      else -> { /* do nothing */ }
    }
  }

  override fun readInlayActionPayload(input: DataInput): InlayActionPayload {
    val type = readINT(input)
    return when (type) {
      0 -> StringInlayActionPayload(readUTF(input))
      1 -> PsiPointerInlayActionPayload(ZombieSmartPointer())
      2 -> SymbolPointerInlayActionPayload(ZombieSymbolPointer())
      else -> throw IllegalStateException("unknown inlay action payload type: $type")
    }
  }

  override fun decorateContent(content: String): String = decorateIfDebug(content)

  private fun decorateIfDebug(content: String): String {
    return if (Registry.`is`("cache.markup.debug")) {
      "$content?"
    } else {
      content
    }
  }
}

private object NecromancyInlayDataExternalizer : InlayDataExternalizer(NecromancyPresentationTreeExternalizer) {
  private const val SERDE_VERSION = 0
  override fun serdeVersion(): Int = SERDE_VERSION + super.serdeVersion()

  override fun writeProviderClass(output: DataOutput, providerClass: Class<*>) {}
  override fun readProviderClass(input: DataInput): Class<*> = ZombieInlayHintsProvider::class.java
}


private class ZombieInlayHintsProvider : InlayHintsProvider {
  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
    throw UnsupportedOperationException("Zombie provider does not support inlay collecting")
  }
}


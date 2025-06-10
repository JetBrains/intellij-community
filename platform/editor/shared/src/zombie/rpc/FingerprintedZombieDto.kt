// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor.zombie.rpc

import com.intellij.openapi.editor.impl.zombie.FingerprintedZombie
import com.intellij.openapi.editor.impl.zombie.FingerprintedZombieImpl
import com.intellij.openapi.editor.impl.zombie.Necromancy
import com.intellij.openapi.editor.impl.zombie.Zombie
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.io.*

@Serializable
@ApiStatus.Internal
data class FingerprintedZombieDto(
  val fingerprint: Long,
  val zombie: List<Byte>,
) {
  fun <Z: Zombie> toFingerprintedZombie(necromancy: Necromancy<Z>): FingerprintedZombie<Z> {
    val zombie =  ByteArrayInputStream(zombie.toByteArray()).use {
      DataInputStream(it).use { dataInput ->
        necromancy.exhumeZombie(dataInput)
      }
    }
    return FingerprintedZombieImpl(fingerprint, zombie)
  }
  companion object {
    fun<Z: Zombie> FingerprintedZombie<Z>.fromFingerprintedZombie(necromancy: Necromancy<Z>): FingerprintedZombieDto {
       return ByteArrayOutputStream().use {bao ->
         DataOutputStream(bao).use { dataOutput: DataOutput ->
           necromancy.buryZombie(dataOutput, zombie())
           FingerprintedZombieDto(fingerprint(), bao.toByteArray().toList())
         }
      }
    }
  }
}
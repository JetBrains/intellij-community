// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelMachineProviderUtil")

package com.intellij.platform.eel.provider

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
suspend fun EelDescriptor.resolveEelMachine(): EelMachine {
  if (this === LocalEelDescriptor) return LocalEelMachine
  return EelMachineResolver.EP_NAME.extensionList.firstNotNullOfOrNull { it.resolveEelMachine(this) }
         ?: throw IllegalStateException("No EelMachine found for descriptor: $this (${this.name})")
}

@ApiStatus.Experimental
fun EelDescriptor.getResolvedEelMachine(): EelMachine? {
  if (this === LocalEelDescriptor) return LocalEelMachine
  return EelMachineResolver.EP_NAME.extensionList.firstNotNullOfOrNull {
    it.getResolvedEelMachine(this)
  }
}

@ApiStatus.Experimental
suspend fun EelDescriptor.toEelApi(): EelApi {
  return resolveEelMachine().toEelApi(this)
}

@ApiStatus.Experimental
interface EelMachineResolver {
  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<EelMachineResolver> = ExtensionPointName("com.intellij.eelMachineResolver")

    @ApiStatus.Internal
    suspend fun getEelMachineByInternalName(internalName: String): EelMachine {
      if (internalName == LocalEelMachine.internalName) {
        return LocalEelMachine
      }

      return EP_NAME.extensionList.firstNotNullOfOrNull {
        it.resolveEelMachineByInternalName(internalName)
      } ?: throw IllegalStateException("No EelMachine found for internal name: $internalName")
    }
  }

  @ApiStatus.Internal
  fun getResolvedEelMachine(eelDescriptor: EelDescriptor): EelMachine?

  suspend fun resolveEelMachine(eelDescriptor: EelDescriptor): EelMachine?

  @ApiStatus.Internal
  suspend fun resolveEelMachineByInternalName(internalName: String): EelMachine?
}
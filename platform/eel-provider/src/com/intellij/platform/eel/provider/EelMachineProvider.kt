// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelMachineProviderUtil")

package com.intellij.platform.eel.provider

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
suspend fun EelDescriptor.getEelMachine(): EelMachine {
  if (this === LocalEelDescriptor) return LocalEelMachine
  return EelMachineProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.getEelMachine(this) }
         ?: throw IllegalStateException("No EelMachine found for descriptor: $this")
}

@ApiStatus.Experimental
fun EelDescriptor.getResolvedEelMachine(): EelMachine? {
  if (this === LocalEelDescriptor) return LocalEelMachine
  return EelMachineProvider.EP_NAME.extensionList.firstNotNullOfOrNull {
    it.getResolvedEelMachine(this)
  }
}

@ApiStatus.Experimental
suspend fun EelDescriptor.toEelApi(): EelApi {
  return getEelMachine().toEelApi(this)
}

@ApiStatus.Experimental
interface EelMachineProvider {
  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<EelMachineProvider> = ExtensionPointName("com.intellij.eelMachineProvider")

    @ApiStatus.Internal
    suspend fun getEelMachineByInternalName(internalName: String): EelMachine {
      if (internalName == LocalEelMachine.internalName) {
        return LocalEelMachine
      }

      return EP_NAME.extensionList.firstNotNullOfOrNull {
        it.getEelMachineByInternalName(internalName)
      } ?: throw IllegalStateException("No EelMachine found for internal name: $internalName")
    }
  }

  @ApiStatus.Internal
  fun getResolvedEelMachine(eelDescriptor: EelDescriptor): EelMachine?

  suspend fun getEelMachine(eelDescriptor: EelDescriptor): EelMachine?

  @ApiStatus.Internal
  suspend fun getEelMachineByInternalName(internalName: String): EelMachine?
}
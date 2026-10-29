// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.provider

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelMachineConnectionProvider
import com.intellij.platform.eel.provider.EelMachineResolver
import com.intellij.platform.eel.provider.EelMachineResolver.Provider.DockerEelDescriptorProvider
import com.intellij.platform.eel.provider.EelMachineResolver.Provider.DockerEelMachineProvider
import com.intellij.platform.eel.provider.EelMachineResolver.Provider.SshEelDescriptorProvider
import com.intellij.platform.eel.provider.EelMachineResolver.Provider.SshEelMachineProvider
import com.intellij.platform.eel.provider.EelMachineResolver.Provider.WslEelDescriptorProvider
import com.intellij.platform.eel.provider.EelMachineResolver.Provider.WslEelMachineProvider
import org.jetbrains.annotations.ApiStatus

internal class EelMachineResolverProviderImpl : EelMachineResolver.Provider {
  private companion object {
    val EP_NAME_WSL_DESCRIPTOR: ExtensionPointName<WslEelDescriptorProvider> = ExtensionPointName("com.intellij.wslEelDescriptorProvider")
    val EP_NAME_WSL_MACHINE: ExtensionPointName<WslEelMachineProvider> = ExtensionPointName("com.intellij.wslEelMachineProvider")
    val EP_NAME_DOCKER_DESCRIPTOR: ExtensionPointName<DockerEelDescriptorProvider> = ExtensionPointName("com.intellij.dockerEelDescriptorProvider")
    val EP_NAME_DOCKER_MACHINE: ExtensionPointName<DockerEelMachineProvider> = ExtensionPointName("com.intellij.dockerEelMachineProvider")
    val EP_NAME_SSH_DESCRIPTOR: ExtensionPointName<SshEelDescriptorProvider> = ExtensionPointName("com.intellij.sshEelDescriptorProvider")
    val EP_NAME_SSH_MACHINE: ExtensionPointName<SshEelMachineProvider> = ExtensionPointName("com.intellij.sshEelMachineProvider")
  }

  override fun getAll(): List<EelMachineResolver> {
    return EelMachineResolverEpBridge.EP_NAME.extensionList
  }

  override fun wsl(): List<WslEelDescriptorProvider> {
    return EP_NAME_WSL_DESCRIPTOR.extensionList
  }

  override fun wslMachines(): List<WslEelMachineProvider> {
    return EP_NAME_WSL_MACHINE.extensionList
  }

  override fun docker(): List<DockerEelDescriptorProvider> {
    return EP_NAME_DOCKER_DESCRIPTOR.extensionList
  }

  override fun dockerMachines(): List<DockerEelMachineProvider> {
    return EP_NAME_DOCKER_MACHINE.extensionList
  }

  override fun ssh(): List<SshEelDescriptorProvider> {
    return EP_NAME_SSH_DESCRIPTOR.extensionList
  }

  override fun sshMachines(): List<SshEelMachineProvider> {
    return EP_NAME_SSH_MACHINE.extensionList
  }
}

@ApiStatus.Internal
object EelMachineResolverEpBridge {
  val EP_NAME: ExtensionPointName<EelMachineResolver> = ExtensionPointName("com.intellij.eelMachineResolver")
}

internal class EelMachineConnectionProviderBridgeImpl : EelMachineConnectionProvider.Bridge {
  override fun getAll(): List<EelMachineConnectionProvider> {
    return EelMachineConnectionProviderEpBridge.EP_NAME.extensionList
  }
}

@ApiStatus.Internal
object EelMachineConnectionProviderEpBridge {
  val EP_NAME: ExtensionPointName<EelMachineConnectionProvider> = ExtensionPointName("com.intellij.eelMachineConnectionProvider")
}

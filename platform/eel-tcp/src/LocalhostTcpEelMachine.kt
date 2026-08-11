// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.provider.EelMachineResolver
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.ijent.IjentExecFileProvider
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.platform.ijent.getIjentGrpcArgv
import com.intellij.platform.ijent.spi.IjentConnectionContext
import com.intellij.platform.ijent.spi.IjentConnectionStrategy
import com.intellij.platform.ijent.spi.IjentDeployingStrategy
import com.intellij.platform.ijent.spi.IjentSessionProcessMediator
import com.intellij.platform.ijent.spi.IjentSessionProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import kotlin.io.path.absolutePathString

object LocalhostTcpEelDescriptor : TcpEelDescriptor(LocalEelDescriptor.osFamily) {
  override val rootPathString: String =
    "${TcpEelConstants.TCP_PATH_PREFIX}$LOCALHOST_TCP_EEL_INTERNAL_NAME-${osFamily.name.lowercase()}"
  override val name: @NonNls String = "Localhost EEL"
}

class LocalhostTcpEelMachine(
  private val parentScope: ParentOfIjentScopes,
) : TcpEelMachine(LOCALHOST_TCP_EEL_INTERNAL_NAME) {
  override suspend fun createStrategy(): IjentDeployingStrategy {
    return object : IjentDeployingStrategy {
      override suspend fun createIjentSession(provider: IjentSessionProvider): IjentSession {
        val platform = localEel.platform
        val ijentBinary = serviceAsync<IjentExecFileProvider>().getIjentBinary(platform).absolutePathString()
        val process = withContext(Dispatchers.IO) {
          ProcessBuilder(getIjentGrpcArgv(ijentBinary))
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        }
        val mediator = IjentSessionProcessMediator.create(parentScope, process, "localhost-tcp-eel")
        return provider.connect(IjentConnectionContext(
          mediator = mediator,
          targetPlatform = platform,
          connectionStrategy = IjentConnectionStrategy.Default,
        ))
      }
    }
  }
}

class LocalhostTcpEelMachineResolver(coroutineScope: CoroutineScope) : EelMachineResolver {
  private val machine = LocalhostTcpEelMachine(ParentOfIjentScopes(coroutineScope))

  override fun getResolvedEelMachine(eelDescriptor: EelDescriptor): EelMachine? =
    machine.takeIf { it.ownsDescriptor(eelDescriptor) }

  override suspend fun resolveEelMachine(eelDescriptor: EelDescriptor): EelMachine? =
    getResolvedEelMachine(eelDescriptor)

  override suspend fun resolveEelMachineByInternalName(internalName: String): EelMachine? =
    machine.takeIf { it.internalName == internalName }
}

class LocalhostTcpEelPathParser : TcpEelPathParser {
  override fun isInternalNameCompatible(internalName: String): Boolean =
    internalName == LOCALHOST_TCP_EEL_INTERNAL_NAME

  override fun toDescriptor(internalName: String, osFamily: EelOsFamily): TcpEelDescriptor? =
    LocalhostTcpEelDescriptor.takeIf { isInternalNameCompatible(internalName) && it.osFamily == osFamily }
}

const val LOCALHOST_TCP_EEL_INTERNAL_NAME: String = "eel-localhost"

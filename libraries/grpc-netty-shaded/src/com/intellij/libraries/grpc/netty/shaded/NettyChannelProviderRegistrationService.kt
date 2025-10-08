package com.intellij.libraries.grpc.netty.shaded

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import io.grpc.ManagedChannelRegistry
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelProvider
import org.jetbrains.annotations.ApiStatus

/**
 * [ManagedChannelRegistry.getDefaultRegistry] has the ability to discover available [io.grpc.ManagedChannelProvider] subclasses.
 * However, [ManagedChannelRegistry] uses a classloader that has no access to [NettyChannelProvider],
 * because intellij.libraries.grpc module does not depend on intellij.libraries.grpc.netty.shaded,
 * so automatic discovery fails.
 */
@Service(Service.Level.APP)
@ApiStatus.Experimental
class NettyChannelProviderRegistrationService : Disposable {
  private val registry = ManagedChannelRegistry.getDefaultRegistry()
  private val channelProvider = NettyChannelProvider()

  init {
    registry.register(channelProvider)
  }

  override fun dispose() {
    registry.deregister(channelProvider)
  }

  companion object {
    fun ensureChannelProviderRegistered() {
      service<NettyChannelProviderRegistrationService>()
    }
  }
}

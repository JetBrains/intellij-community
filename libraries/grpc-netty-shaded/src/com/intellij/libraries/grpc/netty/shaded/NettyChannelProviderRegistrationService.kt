package com.intellij.libraries.grpc.netty.shaded

import io.grpc.ManagedChannelRegistry
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelProvider
import org.jetbrains.annotations.ApiStatus

/**
 * [ManagedChannelRegistry.getDefaultRegistry] has the ability to discover available [io.grpc.ManagedChannelProvider] subclasses.
 * However, [ManagedChannelRegistry] uses a classloader that has no access to [NettyChannelProvider],
 * because intellij.libraries.grpc module does not depend on intellij.libraries.grpc.netty.shaded,
 * so automatic discovery fails.
 */
@ApiStatus.Experimental
object NettyChannelProviderRegistrationService {
  private val registered: Unit by lazy {
    ManagedChannelRegistry.getDefaultRegistry().register(NettyChannelProvider())
  }

  @JvmStatic
  fun ensureChannelProviderRegistered() {
    registered
  }
}

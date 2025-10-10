// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ijent.tcp.TcpEndpoint
import java.util.concurrent.ConcurrentHashMap

@Service
internal class TcpEelRegistry {
  companion object {
    fun getInstance(): TcpEelRegistry = service()
    private val LOG = logger<TcpEelRegistry>()
  }
  private val registry = ConcurrentHashMap<TcpEndpoint, TcpEelDescriptor>()
  fun register(endpoint: TcpEndpoint) {
    registry.compute(endpoint) { _, oldValue ->
      if (oldValue == null) {
          LOG.info("Creating TCP descriptor for $endpoint")
        TcpEelDescriptor(endpoint)
      } else {
        oldValue
      }
    }
  }
  fun get(endpoint: TcpEndpoint): TcpEelDescriptor? {
    return registry[endpoint]
  }
}
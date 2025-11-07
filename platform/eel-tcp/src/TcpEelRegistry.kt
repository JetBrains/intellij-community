// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import java.util.concurrent.ConcurrentHashMap

@Service
internal class TcpEelRegistry {
  companion object {
    fun getInstance(): TcpEelRegistry = service()
    private val LOG = logger<TcpEelRegistry>()
  }

  private val registry = ConcurrentHashMap<String, TcpEelDescriptor>()
  fun register(internalName: String): TcpEelDescriptor {
    return registry.compute(internalName) { _, oldValue ->
      if (oldValue == null) {
        LOG.info("Creating TCP descriptor for $internalName")
        tryRegister(internalName)
      } else {
        oldValue
      }
    }!!
  }

  private fun tryRegister(internalName: String): TcpEelDescriptor? = TcpEelPathParser.toDescriptor(internalName)

  fun get(internalName: String): TcpEelDescriptor? {
    return registry[internalName] ?: register(internalName)
  }
}
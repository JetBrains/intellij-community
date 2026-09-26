// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent

internal fun classFromAgentJar(): Class<*> {
  return com.intellij.memory.agent.MemoryAgent::class.java
}

internal fun extractProxy(): ByteArray {
  val resourcePath = "com/intellij/memory/agent/IdeaNativeAgentProxy.class"
  val stream = classFromAgentJar().classLoader.getResourceAsStream(resourcePath)
               ?: error("Cannot load memory agent proxy class $resourcePath")
  return stream.use { it.readAllBytes() }
}


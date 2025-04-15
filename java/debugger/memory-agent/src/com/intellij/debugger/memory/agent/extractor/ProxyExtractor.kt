// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent.extractor

class ProxyExtractor {
  fun extractProxy(): ByteArray =
    ProxyExtractor::class.java.classLoader.getResourceAsStream("com/intellij/memory/agent/IdeaNativeAgentProxy.class")!!.readAllBytes()
}

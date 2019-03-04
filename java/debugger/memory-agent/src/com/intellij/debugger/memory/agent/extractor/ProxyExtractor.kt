// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent.extractor

import org.apache.commons.io.IOUtils

class ProxyExtractor {
  fun extractProxy(): ByteArray {
    return IOUtils.toByteArray(ProxyExtractor::class.java.classLoader.getResourceAsStream("proxy/IdeaNativeAgentProxy.class"))
  }
}

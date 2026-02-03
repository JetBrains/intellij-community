// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.openapi.util.registry.Registry

// TODO IJPL-207762 cleanup
object RpcCompletionStat {
  private val prefixMatcherMap = mutableMapOf<String, Int>()

  fun registerMatcher(matcher: PrefixMatcher) {
    if (Registry.`is`("remdev.completion.on.frontend.stat")) {
      prefixMatcherMap.merge(matcher.javaClass.name, 1, Int::plus)
    }
  }

  fun dumpToStdout() {
    if (Registry.`is`("remdev.completion.on.frontend.stat")) {
      println(prefixMatcherMap.entries.sortedBy { it.value }.joinToString(separator = "\n") { (key, value) -> "$key: $value" })
    }
  }
}
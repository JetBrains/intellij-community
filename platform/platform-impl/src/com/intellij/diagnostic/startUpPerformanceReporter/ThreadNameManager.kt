// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.startUpPerformanceReporter

import com.intellij.diagnostic.ActivityImpl
import gnu.trove.TLongObjectHashMap

internal class ThreadNameManager {
  // ConcurrencyUtil.runUnderThreadName is used in our code (to make thread dumps more clear) and changes thread name,
  // so, use first thread name that associated with thread and not subsequent one
  private val idToName = TLongObjectHashMap<String>()

  fun getThreadName(event: ActivityImpl): String {
    var result = idToName.get(event.threadId)
    if (result != null) {
      return result
    }

    val name = event.threadName
    result = when {
      name.startsWith("AWT-EventQueue-") -> "edt"
      name.startsWith("Idea Main Thread") -> "idea main"
      name.startsWith("ApplicationImpl pooled thread ") -> name.replace("ApplicationImpl pooled thread ", "pooled ")
      else -> name
    }
    idToName.put(event.threadId, result)
    return result
  }
}
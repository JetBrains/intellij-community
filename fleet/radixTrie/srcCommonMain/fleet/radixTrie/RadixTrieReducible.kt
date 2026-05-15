// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.radixTrie

import kotlin.jvm.JvmInline

@JvmInline
value class RadixTrieReduceDecision(private val continueReduce: Boolean) {
  companion object {
    val Continue: RadixTrieReduceDecision = RadixTrieReduceDecision(true)
    val Stop: RadixTrieReduceDecision = RadixTrieReduceDecision(false)
  }
}


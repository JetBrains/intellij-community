// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb.impl

import com.jetbrains.rhizomedb.ReduceDecision
import fleet.radixTrie.RadixTrie
import fleet.radixTrie.RadixTrieReduceDecision

private fun RadixTrieReduceDecision.asReduceDecision(): ReduceDecision =
  when (this) {
    RadixTrieReduceDecision.Continue -> ReduceDecision.Continue
    RadixTrieReduceDecision.Stop -> ReduceDecision.Stop
    else -> error("Unexpected RadixTrieReduceDecision value: $this")
  }

private fun ReduceDecision.asRadixTrieReduceDecision(): RadixTrieReduceDecision =
  when (this) {
    ReduceDecision.Continue -> RadixTrieReduceDecision.Continue
    ReduceDecision.Stop -> RadixTrieReduceDecision.Stop
    else -> error("Unexpected ReduceDecision value: $this")
  }

internal inline fun <V : Any> RadixTrie<V>.reduceRhizome(crossinline reducer: (Int, V) -> ReduceDecision): ReduceDecision =
  reduce { key, value ->
    reducer(key, value).asRadixTrieReduceDecision()
  }.asReduceDecision()

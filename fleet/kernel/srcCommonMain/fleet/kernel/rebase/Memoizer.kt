// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import fleet.multiplatform.shims.ConcurrentHashMap

internal class Memoizer<T> {
  data class WithEpoch<T>(val value: T, val epoch: Int)

  private val m = ConcurrentHashMap<Any, WithEpoch<T>>()
  private var epoch: Int = 0

  fun nextEpoch() {
    epoch++
  }

  fun memo(unique: Boolean, key: Any, f: () -> T): T {
    return m.compute(key) { _, v ->
      when {
        v == null -> WithEpoch(f(), epoch)
        unique && v.epoch == epoch -> throw IllegalArgumentException("key $key is not unique in scope. If this happens in a shared block, you might want to use [SharedChangeScope.withKey]")
        else -> v.copy(epoch = epoch)
      }
    }!!.value
  }
}

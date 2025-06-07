// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete.impl

import fleet.kernel.rete.*

internal class BroadcasterImpl<T> : AdaptiveSet<Collector<T>>(), Broadcaster<T> {
  override fun SubscriptionScope.collectImpl(emit: Collector<T>) {
    add(emit)
    onDispose {
      remove(emit)
    }
  }

  override fun invoke(token: Token<T>) {
    forEach { coll -> coll(token) }
  }
}


// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete.impl

import fleet.kernel.rete.Subscription

internal open class SubscriptionTree(val parent: SubscriptionTree?) : AdaptiveSet<Subscription>(), Subscription {
  private var closed = false

  fun isClosed(): Boolean = closed

  fun attach(sub: Subscription) {
    require(!closed) {
      "already closed"
    }
    add(sub)
  }

  fun attachTree(): SubscriptionTree {
    val s = SubscriptionTree(this)
    attach(s)
    return s
  }

  override fun close() {
    val self = this
    parent?.let { p ->
      if (!p.closed) {
        p.remove(self)
      }
    }
    closed = true
    forEach {
      it.close()
    }
  }
}

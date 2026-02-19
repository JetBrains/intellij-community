// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete.impl

import com.jetbrains.rhizomedb.Attribute
import com.jetbrains.rhizomedb.EID
import fleet.fastutil.longs.LongSet
import fleet.kernel.rete.Cardinality
import fleet.kernel.rete.Collector
import fleet.kernel.rete.DatomPort
import fleet.kernel.rete.Producer
import fleet.kernel.rete.Query
import fleet.kernel.rete.QueryScope
import fleet.kernel.rete.RevalidationPort
import fleet.kernel.rete.Subscription
import fleet.kernel.rete.SubscriptionScope

internal object DummyQueryScope : QueryScope {
  override fun <C : Cardinality, T> Query<C, T>.producer(): Producer<T> = producerImpl()
  override val performAdditionalChecks: Boolean = false

  override fun onDispose(sub: Subscription) {}
  override fun subscribe(e: EID?, attribute: Attribute<*>?, v: Any?, datomPort: DatomPort) {}
  override fun subscribe(patterns: LongSet, port: RevalidationPort) {}
  override fun <T> Producer<T>.collect(emit: Collector<T>) {
    collectImpl(emit)
  }

  override fun scope(body: SubscriptionScope.() -> Unit): Subscription {
    this.body()
    return Subscription { }
  }
}
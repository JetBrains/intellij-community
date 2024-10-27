// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete.impl

import com.jetbrains.rhizomedb.Attribute
import com.jetbrains.rhizomedb.EID
import fleet.kernel.rete.*
import it.unimi.dsi.fastutil.longs.LongSet

internal object DummyQueryScope : QueryScope {
  override fun <T> Query<T>.producer(): Producer<T> = producerImpl()
  override fun onDispose(sub: Subscription) { }
  override fun subscribe(e: EID?, attribute: Attribute<*>?, v: Any?, datomPort: DatomPort) {}
  override fun subscribe(patterns: LongSet, port: RevalidationPort) { }
  override fun <T> Producer<T>.collect(emit: Collector<T>) { collectImpl(emit) }
  override fun scope(body: SubscriptionScope.() -> Unit): Subscription {
    this.body()
    return Subscription {  }
  }
}
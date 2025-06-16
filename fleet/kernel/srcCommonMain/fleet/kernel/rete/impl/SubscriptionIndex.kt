// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete.impl

import com.jetbrains.rhizomedb.Attribute
import com.jetbrains.rhizomedb.EID
import fleet.kernel.rete.DatomPort
import fleet.kernel.rete.RevalidationPort
import fleet.kernel.rete.Subscription
import fleet.fastutil.longs.LongSet
import kotlin.jvm.JvmInline

@JvmInline
internal value class SubscriptionsIndex(private val patternIndex: PatternIndex<PatternIndexEntry> = PatternIndex()) {
  sealed class PatternIndexEntry {
    abstract val node: Node

    class RevalidationEntry(override val node: Node,
                            val depth: Int,
                            val port: RevalidationPort
    ) : PatternIndexEntry()

    class DatomEntry(override val node: Node,
                     val eid: EID?,
                     val attribute: Attribute<*>?,
                     val value: Any?,
                     val depth: Int,
                     val port: DatomPort
    ) : PatternIndexEntry()
  }

  fun sub(key: PatternIndexEntry, patterns: LongSet): Subscription {
    patternIndex.update(key, patterns)
    return Subscription {
      patternIndex.update(key, LongSet.of())
    }
  }

  fun updatePatterns(revalidationEntry: PatternIndexEntry.RevalidationEntry, patterns: LongSet) {
    patternIndex.update(revalidationEntry, patterns)
  }

  fun query(eid: EID, attr: Attribute<*>, value: Any): Iterable<PatternIndexEntry> =
    patternIndex.get(eid, attr, value).filter { entry ->
      when (entry) {
        is PatternIndexEntry.DatomEntry -> {
          (entry.eid == null || entry.eid == eid) &&
          (entry.attribute == null || entry.attribute == attr) &&
          (entry.value == null || entry.value == value)
        }
        is PatternIndexEntry.RevalidationEntry -> true
      }
    }
}

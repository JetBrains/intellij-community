// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete

import com.jetbrains.rhizomedb.*
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap

internal fun <T : Any> SubscriptionScope.getAttribute(query: Producer<EID>, attribute: Attribute<T>): Producer<T> = run {
  // eid -> #{input-matches}
  class Matches(val matches: MutableSet<Match<EID>>,
                val sub: Subscription)

  val memory = Int2ObjectOpenHashMap<Matches>()
  val broadcast: Broadcaster<T> = Broadcaster()
  query.collect { token ->
    val match = token.match
    val eid = match.value
    when (token.added) {
      true -> {
        memory.getOrPut(eid) {
          val matches = adaptiveSetOf<Match<EID>>()
          Matches(matches,
                  scope {
                    subscribe(eid, attribute, null) { datom ->
                      matches.forEach { match ->
                        broadcast(Token(datom.added, datom.eav.valueMatch(match) as Match<T>))
                      }
                    }
                  })
        }.matches.add(match)
      }
      false -> {
        memory.removeIf(eid) { matches ->
          matches.matches.remove(match)
          val isEmpty = matches.matches.isEmpty()
          if (isEmpty) {
            matches.sub.close()
          }
          isEmpty
        }
      }
    }
    val datoms = DbContext.threadBound.queryIndex(IndexQuery.GetMany(eid, attribute))
    datoms.forEach { datom ->
      broadcast(Token(token.added, datom.eav.valueMatch(match) as Match<T>))
    }
  }

  Producer { emit ->
    memory.forEach { eid, matches ->
      val datoms = DbContext.threadBound.queryIndex(IndexQuery.GetMany(eid, attribute))
      matches.matches.forEach { match ->
        datoms.forEach { datom ->
          emit(Token(true, datom.eav.valueMatch(match) as Match<T>))
        }
      }
    }
    broadcast.collect(emit)
  }
}

internal fun <T : Any> SubscriptionScope.lookupAttribute(query: Producer<T>, attribute: Attribute<T>): Producer<EID> = run {
  // T -> #{input-matches}
  class Matches(val matches: MutableSet<Match<T>>,
                val sub: Subscription)

  val memory = adaptiveMapOf<T, Matches>()
  val broadcast: Broadcaster<EID> = Broadcaster()
  query.collect { token ->
    val match = token.match
    val value = match.value
    when (token.added) {
      true -> {
        memory.getOrPut(value) {
          val matches = adaptiveSetOf<Match<T>>()
          Matches(matches,
                  scope {
                    subscribe(null, attribute, value) { datom ->
                      matches.forEach { match ->
                        broadcast(Token(datom.added, datom.eav.eidMatch(match)))
                      }
                    }
                  })
        }.matches.add(match)
      }
      false -> {
        memory.removeIf(value) { matches ->
          matches.matches.remove(match)
          val isEmpty = matches.matches.isEmpty()
          if (isEmpty) {
            matches.sub.close()
          }
          isEmpty
        }
      }
    }
    val datoms = DbContext.threadBound.queryIndex(IndexQuery.LookupMany(attribute, value))
    datoms.forEach { datom ->
      broadcast(Token(token.added, datom.eav.eidMatch(match)))
    }
  }

  Producer { emit ->
    memory.forEach { value, matches ->
      val datoms = DbContext.threadBound.queryIndex(IndexQuery.LookupMany(attribute, value))
      matches.matches.forEach { match ->
        datoms.forEach { datom ->
          emit(Token(true, datom.eav.eidMatch(match)))
        }
      }
    }
    broadcast.collect(emit)
  }
}

internal fun SubscriptionScope.column(attribute: Attribute<*>): Producer<EAV> = run {
  val broadcast = Broadcaster<EAV>()
  subscribe(null, attribute, null) { datom ->
    broadcast(Token(datom.added, Match.validatable(
      value = EAV(datom.eid, datom.attr, datom.value),
      validate = containsDatom(datom.eid, datom.attr, datom.value)
    )))
  }
  Producer { emit ->
    DbContext.threadBound.queryIndex(IndexQuery.Column(attribute)).forEach { datom ->
      emit(Token(true, Match.validatable(
        value = EAV(datom.eid, datom.attr, datom.value),
        validate = containsDatom(datom.eid, datom.attr, datom.value)
      )))
    }
    broadcast.collect(emit)
  }
}

internal fun EAV.eidMatch(base: Match<*>?): Match<EID> = let { datom ->
  Match.validatable(datom.eid, base, containsDatom(datom.eid, datom.attr, datom.value))
}

internal fun EAV.valueMatch(base: Match<*>?): Match<Any> = let { datom ->
  Match.validatable(datom.value, base, containsDatom(datom.eid, datom.attr, datom.value))
}

private fun containsDatom(eid: EID, attr: Attribute<*>, value: Any): () -> ValidationResultEnum = {
  (DbContext.threadBound.queryIndex(IndexQuery.Contains(
    eid = eid,
    attribute = attr as Attribute<Any>,
    value = value)
  ) != null).asValidationResult()
}

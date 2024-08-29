// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete

import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.impl.LegacySchema
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlin.reflect.KProperty1

internal fun <E : LegacyEntity, T : Any> SubscriptionScope.get(producer: Producer<E>, property: KProperty1<E, *>): Producer<T> = run {
  withAttribute(property) { attribute ->
    val attrValues = getAttribute(producer.rawMap { it.value.eid }, attribute)
    when {
      attribute.schema.isRef ->
        (attrValues as Producer<EID>).rawMap { entity(it.value) as T }
      else ->
        attrValues as Producer<T>
    }
  }
}

internal fun <E : LegacyEntity, T : Any> SubscriptionScope.lookup(producer: Producer<T>, property: KProperty1<E, T?>): Producer<E> = run {
  withAttribute(property) { attribute ->
    val attributeValues = when {
      attribute.schema.isRef -> producer.rawMap { (it.value as Entity).eid }
      else -> producer
    }
    lookupAttribute(attributeValues, attribute as Attribute<Any>)
      .rawMap { entity(it.value) as E }
  }
}

internal fun <U> SubscriptionScope.withAttribute(property: KProperty1<out LegacyEntity, *>,
                                                 delayedQuery: (Attribute<*>) -> Producer<U>): Producer<U> = let { outerScope ->
  when (val attribute = attribute(property)) {
    null -> {
      var sub: Subscription? = null
      var delayedProducer: Producer<U>? = null
      val broadcast = Broadcaster<U>()
      sub = scope {
        subscribe(null, LegacySchema.Attr.kProperty, property) { datom ->
          sub?.close()
          sub = null
          val p = delayedQuery(Attribute<Any>(datom.eid))
          delayedProducer = p
          outerScope.run { p.collect(broadcast) }
        }
      }
      Producer { emit ->
        when (val p = delayedProducer) {
          null -> broadcast.collect(emit)
          else -> p.collect(emit)
        }
      }
    }
    else -> delayedQuery(attribute)
  }
}

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

internal fun <E : LegacyEntity, T> SubscriptionScope.column(property: KProperty1<E, T>): Producer<Pair<E, T>> =
  withAttribute(property) { attribute ->
    column(attribute).rawMap { datomMatch ->
      val datom = datomMatch.value
      val entity = entity(datom.eid) as E
      val value = when {
        attribute.schema.isRef -> entity(datom.value as EID)
        else -> datom.value
      } as T
      entity to value
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

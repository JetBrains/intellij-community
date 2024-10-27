// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete

import com.jetbrains.rhizomedb.*
import fleet.kernel.rete.impl.*
import fleet.kernel.rete.impl.DummyQueryScope
import fleet.kernel.rete.impl.distinct

/**
 * Emits a single unconditional match with the given value [t]
 * */
fun <T> queryOf(t: T): Query<T> = Query {
  val match = Match.of(t)
  Producer { emit -> emit(Token(true, match)) }
}

/**
 * Converts an [Iterable] to a [Query] producing elemnts as [Match]es
 * */
fun <T> Iterable<T>.asQuery(): Query<T> = let { iter ->
  Query {
    val tokens = iter.map { t -> Token(true, Match.of(t)) }
    Producer { emit -> tokens.forEach(emit::invoke) }
  }
}

/**
 * Provides a match for every entity of a given [EntityType].
 * */
fun <E : Entity> EntityType<E>.each(): Query<E> = let { entityType ->
  queryOf(entityType.eid)
    .lookupAttribute(Entity.Type.attr as Attribute<EID>)
    .rawMap { eid ->
      @Suppress("UNCHECKED_CAST")
      (entity(eid.value) as? E) ?: error("entity does not exist for ${entityType.entityTypeIdent}")
    }
    .intern("each", entityType)
}

/**
 * Query<Unit> can be seen as predicate.
 * It yields Unit when it becomes true, and retracts it back when falsified.
 * */
typealias PredicateQuery = Query<Unit>

/**
 * Emits receiver as long as it exists
 * */
fun <E : Entity> E.asQuery(): Query<E> = let { entity ->
  queryOf(entity.eid)
    .getAttribute(Entity.Type.attr as Attribute<EID>)
    .rawMap { entity }
}

/**
 * Emits [Unit] until entity is retracted
 * */
fun Entity.existence(): PredicateQuery = let { e ->
  queryOf(e.eid)
    .getAttribute(Entity.Type.attr as Attribute<EID>)
    .rawMap { }
  //    .intern("exists", this)
}

/**
 * Provides a union of two queries.
 * Emits matches from both queries in no particular order, or uniqueness guearantees
 * */
infix fun <T> Query<T>.union(rhs: Query<T>): Query<T> = let { lhs ->
  Query {
    val lhsProducer = lhs.producer()
    val rhsProducer = rhs.producer()
    Producer { emit ->
      lhsProducer.collect(emit)
      rhsProducer.collect(emit)
    }
  }
}

/**
 * Cartesian product of two queries
 * Produces all pairs of matches, where the first element is the match from the lhs, and the second element is from rhs
 * */
infix fun <T, U> Query<T>.product(rhs: Query<U>): Query<Pair<T, U>> = let { lhs ->
  Query {
    val lhsProducer = lhs.producer()
    val rhsProducer = rhs.producer()
    rawJoinOn(left = lhsProducer,
              onLeft = { Unit },
              right = rhsProducer,
              onRight = { Unit })
      .rawMap { m -> m.value.left to m.value.right }
  }
}

/**
 * maps every match of [Query] to a set of values, provided a *pure* function of a database [f].
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * may yield repeated values if [f] is not injective
 * unlike values, [Match]es will be unique in some way
 * */
fun <T, U> Query<T>.flatMap(f: (T) -> Set<U>): Query<U> = let { query ->
  Query { flatMap(query.producer()) { match -> f(match.value) } }
}

/**
 * version of a [flatMap] working with [Match]
 * [f] has to be a *pure* function of a database
 * unlike values, [Match]es will be unique in some way
 * */
fun <T, U> Query<T>.flatMapMatch(f: (Match<T>) -> Set<U>): Query<U> = let { query ->
  Query { flatMap(query.producer(), f) }
}

/**
 * maps a database snapshot to a single value of [f]
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * */
fun <T> query(f: () -> T): Query<T> = queryOf(Unit).flatMap { setOf(f()) }

/**
 * maps a database snapshot to a single value of [f] unless it returned null
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * */
fun <T : Any> queryNotNull(f: () -> T?): Query<T> = queryOf(Unit).flatMap { setOfNotNull(f()) }

/**
 * maps a database snapshot to a set of values returned by [f], yielding them as matches
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * */
fun <T> queryMany(f: () -> Set<T>): Query<T> = queryOf(Unit).flatMap { f() }

/**
 * yields Unit when predicate [p] is true
 * [p] is being read-tracked and recalculated whenever related data in the db changes
 * [p] has to be a *pure* function of a database
 * */
fun predicateQuery(p: () -> Boolean): Query<Unit> = queryOf(Unit).filter { p() }

/**
 * [p] is applied to the values of incoming matches and propagates them forward if true,
 * [p] is being read-tracked
 * [p] has to be a *pure* function of a database
 * */
fun <T> Query<T>.filter(p: (T) -> Boolean): Query<T> =
  flatMap { t -> if (p(t)) setOf(t) else emptySet() }

/**
 * version of [filter] working with [Match]
 * [p] has to be a *pure* function of a database
 * */
fun <T> Query<T>.filterMatch(p: (Match<T>) -> Boolean): Query<T> =
  flatMapMatch { t -> if (p(t)) setOf(t.value) else emptySet() }

/**
 * maps values produced by query with a pure function [f]
 * for every match of query will produce a new match
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * may yield repeated values if [f] is not injective
 * unlike values, [Match]es will be unique in some way
 * */
fun <T, U> Query<T>.map(f: (T) -> U): Query<U> = flatMap { t -> setOf(f(t)) }

/**
 * see [map]
 * produces match only if [f] returned non-null
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * */
fun <T, U> Query<T>.mapNotNull(f: (T) -> U?): Query<U> = flatMap { t -> f(t)?.let(::setOf) ?: emptySet() }

/**
 * Returns a query containing only matches of the original query that are not null.
 * */
fun <T> Query<T?>.filterNotNull(): Query<T> = mapNotNull { it }

/**
 * Version of [map] working with [Match]
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * */
fun <T, U> Query<T>.mapMatch(f: (Match<T>) -> U): Query<U> = flatMapMatch { t -> setOf(f(t)) }

/**
 * Version of [mapNotNull] working with [Match]
 * unlike values, [Match]es will be unique in some way
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * */
fun <T, U> Query<T>.mapMatchNotNull(f: (Match<T>) -> U?): Query<U> = flatMapMatch { t -> f(t)?.let(::setOf) ?: emptySet() }

/**
 * Yields Unit whenever receiver produces any match.
 * */
fun <T> Query<T>.any(): Query<Unit> = rawMap { }.distinct()

/**
 * Query does not carry a distinct set of values by default for performance and memory reasons.
 * Sometimes one need to stop propagating duplicates further for performance reasons.
 * */
fun <T : Any> Query<T>.distinct(): Query<T> = let { query ->
  Query { distinct(query.producer()) }
}

/**
 * Merges two queries together to provide pairs of values corresponding to arms, for which a certain value is equal, together with the coinciding value
 * [on] functions are allowed to read the db and will be reactive
 * */
infix fun <L, R, T> JoinHand<L, T>.join(rhs: JoinHand<R, T>): Query<JoinPair<L, R, T>> = let { lhs ->
  joinOn(lhs.query, lhs.on, rhs.query, rhs.on)
}

data class JoinPair<L, R, T>(
  val left: L,
  val right: R,
  val on: T,
)

data class JoinHand<T, U>(
  val query: Query<T>,
  val on: (Match<T>) -> U,
)

/**
 * Makes a [JoinHand] for use in [join]
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * */
fun <T, U> Query<T>.on(f: (T) -> U): JoinHand<T, U> = let { query ->
  JoinHand(query) { match -> f(match.value) }
}

/**
 * version of [on] working with [Match]es
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * */
fun <T, U> Query<T>.onMatch(f: (Match<T>) -> U): JoinHand<T, U> = let { query ->
  JoinHand(query, f)
}

/**
 * Emits a match for every value of an [attribute] of input [Query].
 * Similar to [Entity.get] but for [Query]
 * */
operator fun <E : Entity, T : Any> Query<E>.get(attribute: EntityAttribute<E, T>): Query<T> =
  rawMap { m -> m.value.eid }
    .getAttribute(attribute.attr)
    .rawMap { match -> attribute.fromIndexValue(match.value) }

/**
 * Similar to [Entity.lookup] but for [Query]
 * */
fun <E : Entity, T : Any> Query<T>.lookup(attribute: EntityAttribute<E, T>): Query<E> =
  rawMap { attribute.toIndexValue(it.value) }
    .lookupAttribute(attribute.attr as Attribute<Any>)
    .rawMap { entity(it.value) as E }

/**
 * Column query for [EntityAttribute],
 * Similar to [EntityAttribute.all] but for [Query]
 * */
fun <E : Entity, T : Any> EntityAttribute<E, T>.each(): Query<Pair<E, T>> = let { attribute ->
  Query {
    column(attribute.attr).rawMap { datomMatch ->
      val entity = entity(datomMatch.value.eid) as E
      val value = attribute.fromIndexValue(datomMatch.value.value)
      entity to value
    }
  }
}

/**
 * Related to [getOne], but acts on a raw [Attribute] instead of [kotlin.reflect.KProperty]
 * */
fun <T : Any> Query<EID>.getAttribute(attribute: Attribute<T>): Query<T> = let { query ->
  Query { getAttribute(query.producer(), attribute) }
}

/**
 * Related to [lookup], but acts on a raw [Attribute] instead of [kotlin.reflect.KProperty]
 * */
fun <T : Any> Query<T>.lookupAttribute(attribute: Attribute<T>): Query<EID> = let { query ->
  Query { lookupAttribute(query.producer(), attribute) }
}

/**
 * Maps a query using a pure function [f].
 * [f] is not supposed to read db, as it will not be tracked and thus will not be reactive
 * [f] has to be a *pure* function
 * */
fun <T, U> Query<T>.rawMap(f: (Match<T>) -> U): Query<U> = let { query ->
  Query { query.producer().rawMap(f) }
}

fun <T, U> Producer<T>.rawMap(f: (Match<T>) -> U): Producer<U> = let { producer ->
  producer.transform { token, emit ->
    emit(Token(token.added, token.match.withValue(f(token.match))))
  }
}

/**
 * filter matches of query with [p]
 * [p] is not read-tracked and is not supposed to read the db
 * [p] has to be a *pure* function
 * */
fun <T> Query<T>.rawFilter(p: (Match<T>) -> Boolean): Query<T> = let { query ->
  Query { query.producer().rawFilter(p) }
}

fun <T> Producer<T>.rawFilter(p: (Match<T>) -> Boolean): Producer<T> =
  transform { token, emit ->
    if (p(token.match)) emit(token)
  }

/**
 * The most general way to transform tokens. Analogous to Flow.transform. [f] is not being read-tracked.
 * [f] has to be a *pure* function
 * */
fun <T, U> Query<T>.transform(f: (Token<T>, Collector<U>) -> Unit): Query<U> = let { query ->
  Query { query.producer().transform(f) }
}

fun <T, U> Producer<T>.transform(f: (Token<T>, Collector<U>) -> Unit): Producer<U> = let { producer ->
  Producer { emit -> producer.collect { token -> f(token, emit) } }
}

/**
 * Collects a query with many matches into a single-match query.
 * Produces a [Query] with a single match (at any given time), which accumulates all matches of receiver using function [f]
 * [f] has to be pure and is not supposed to read the db.
 * */
fun <Acc, T> Query<T>.reductions(acc: Acc, f: (Acc, Token<T>) -> Acc): Query<Acc> = let { query ->
  Query {
    var state = acc
    val broadcast = Broadcaster<Acc>()
    query.producer().collect { token ->
      val oldState = state
      val newState = f(state, token)
      if (oldState != newState) {
        broadcast(Token(false, Match.nonValidatable(oldState)))
        broadcast(Token(true, Match.nonValidatable(newState)))
      }
      state = newState
    }
    Producer { emit ->
      emit(Token(true, Match.nonValidatable(state)))
      broadcast.collect(emit)
    }
  }
}

/**
 * Collects a query with multiple integer matches into a single-match query, representing the summed integer value of all matches.
 * Produces a [Query] with a single integer match (at any given time).
 */
fun Query<Int>.sum() = reductions(0) { acc, token ->
  when (token.added) {
    true -> acc + token.value
    false -> acc - token.value
  }
}

/**
 * Runs [Query] just one time, to produce a [Set] of it's matches.
 * */
fun <T> Query<T>.matches(): Set<T> = let { query ->
  DummyQueryScope.run {
    val hs = HashSet<T>()
    query.producer().collect { token ->
      require(token.added)
      hs.add(token.match.value)
    }
    hs
  }
}

/**
 * Yields a [Unit] when both [this] and [rhs] supplied theirs
 * */
infix fun PredicateQuery.and(rhs: PredicateQuery): PredicateQuery = let { lhs ->
  PredicateQuery {
    var lhsState: Match<Unit>? = null
    var rhsState: Match<Unit>? = null
    val broadcast = Broadcaster<Unit>()
    lhs.producer().collect { token ->
      val lhsMatch = token.match
      rhsState?.let { rhsMatch ->
        broadcast(Token(token.added, lhsMatch.combine(rhsMatch, Unit)))
      }
      lhsState = if (token.added) lhsMatch else null
    }
    rhs.producer().collect { token ->
      val rhsMatch = token.match
      lhsState?.let { lhsMatch ->
        broadcast(Token(token.added, lhsMatch.combine(rhsMatch, Unit)))
      }
      rhsState = if (token.added) rhsMatch else null
    }
    Producer { emit ->
      lhsState?.let { lhsMatch ->
        rhsState?.let { rhsMatch ->
          emit(Token(true, lhsMatch.combine(rhsMatch, Unit)))
        }
      }
      broadcast.collect(emit)
    }
  }
}

/**
 * Sometimes we need a way to obtain a [Match] of another query.
 * Generally, when the query is published as an API, one should not expose it's internal dependencies.
 * MatchValue is an abstraction over it's value.
 * But when we construct a query using other queries, it might be useful to refer to their matches in order to provide our own.
 *
 * For example, given Query<FooEntity>, we may [getAttribute] on it, yielding Query<Foo>.
 * The resulting query will forget about FooEntity whatsover.
 * There is a way to remember the original value of FooEntity by binding it:
 * ```
 * val fooEntities = entities.bind()
 * ...
 * val foos = fooEntities.getOne(FooEntity::foo)
 * foos.transform { token, emit ->
 *   val fooEntity = token.match[fooEntities]
 *   ...
 * }
 * ```
 * */
fun <T> Query<T>.bind(): BoundQuery<T> = let { query ->
  val key = Any()
  BoundQuery(key, query.transform { token, emit ->
    emit(Token(token.added, token.match.bind(key)))
  })
}

data class BoundQuery<T>(val key: Any, val query: Query<T>) : Query<T> by query

/**
 * [Producer]s of interned queries are shared between their dependant queries, allowing to share the work and save memory.
 * It makes sense to [intern] query if it is used to construct more than one other query.
 * */
inline fun <T> Query<T>.intern(vararg key: Any, crossinline callSiteMarker: () -> Unit = {}): Query<T> =
  /*
   {}.javaClass == {}.javaClass // false
   but
   inline fun inlineFun(): Any = {}.javaClass
   inlineFun() == inlineFun() // true
   but false if called from another module
 */
  internImpl(listOf(*key, { callSiteMarker() }.javaClass))

@PublishedApi
internal fun <T> Query<T>.internImpl(key: Any): Query<T> = 
  InternedQuery(key, this)

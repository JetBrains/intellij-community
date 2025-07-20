// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete

import com.jetbrains.rhizomedb.*
import fleet.kernel.rete.impl.*
import fleet.kernel.rete.impl.DummyQueryScope
import fleet.kernel.rete.impl.distinct
import fleet.util.async.firstNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlin.jvm.JvmName

/**
 * There is always a match in Single, run with it. If the match is invalidate while the body is running, cancel everything.
 */
suspend fun <T, R> StateQuery<T>.withCurrentMatch(f: suspend CoroutineScope.(T) -> R): WithMatchResult<R> {
  return matchesFlow().first().withMatch(f)
}

/**
 * Will wait until Maybe emits a match and then run with it once.
 */
suspend fun <T, R> Query<Maybe, T>.withFirstMatch(f: suspend CoroutineScope.(T) -> R): WithMatchResult<R> {
  return matchesFlow().first().withMatch(f)
}

/**
 * Will try to run [f] when there is a match. If the execution is successfull - returns the result.
 * If the match was invalidated while the body was running - will try again with the next one.
 */
suspend fun <T, R> Query<Maybe, T>.retryWithMatch(f: suspend CoroutineScope.(T) -> R): R {
  return matchesFlow().firstNotNull {
    it.withMatch(f).getOrNull()
  }
}

fun <T> Query<Maybe, T>.orNull(): StateQuery<T?> =
  let { source ->
    Query {
      val sourceProducer = source.producer()
      Producer { emit ->
        var has = false
        emit(Token(true, Match.of(null)))
        sourceProducer.collect { token ->
          when (token.added) {
            true -> {
              check(!has)
              has = true
              emit(Token(false, Match.of(null)))
              emit(token)
            }
            false -> {
              check(has)
              has = false
              emit(token)
              emit(Token(true, Match.of(null)))
            }
          }
        }
      }
    }
  }


@Suppress("UNCHECKED_CAST")
fun <T> Query<Many, T>.singleOrNone(msg: ((List<T>) -> String)? = null): Query<Maybe, T> =
  let { source ->
    Query {
      val none = Any()
      val producer = source.producer()
      Producer { emit ->
        val marker = Any()
        var value: Any? = none
        producer.collect { token ->
          when {
            token.added && value != none -> error("More than one match, ${listOf(value, token.value).let { msg?.invoke(it as List<T>) ?: "values: ${it}" }}")
            token.added && value == none -> value = token.value as Any?
            !token.added && value == none -> error("Nothing to retract")
            !token.added && value != none -> value = none
          }
          emit(token)
        }
      }
    }
  }

/**
 * Emits a single unconditional match with the given value [t]
 * */
fun <T> queryOf(t: T): StateQuery<T> = Query {
  val match = Match.of(t)
  Producer { emit -> emit(Token(true, match)) }
}

/**
 * Converts an [Iterable] to a [Query] producing elemnts as [Match]es
 * */
fun <T> Iterable<T>.asQuery(): Query<Many, T> = let { iter ->
  Query {
    val tokens = iter.map { t -> Token(true, Match.of(t)) }
    Producer { emit -> tokens.forEach(emit::invoke) }
  }
}

/**
 * Provides a match for every entity of a given [EntityType].
 * */
fun <E : Entity> EntityType<E>.each(): SetQuery<E> = let { entityType ->
  @Suppress("UNCHECKED_CAST")
  queryOf(entityType.eid)
    .lookupAttribute(Entity.Type.attr as Attribute<EID>)
    .rawMap { eid ->
      @Suppress("UNCHECKED_CAST")
      (entity(eid.value) as? E) ?: error("entity does not exist for ${entityType.entityTypeIdent}")
    }
    .intern("each", entityType)
}

/**
 * Query<Maybe, Unit> can be seen as predicate.
 * It yields Unit when it becomes true, and retracts it back when falsified.
 * */
typealias PredicateQuery = Query<Maybe, Unit>

/**
 * Emits receiver as long as it exists
 * */
fun <E : Entity> E.asQuery(): StateQuery<E> =
  let { entity ->
    queryOf(entity.eid)
      .getAttribute(Entity.Type.attr)
      .rawMap { entity }
  }

/**
 * Emits [Unit] until entity is retracted
 * */
fun Entity.existence(): PredicateQuery = let { e ->
  queryOf(e.eid)
    .getAttribute(Entity.Type.attr)
    .rawMap { }
}

/**
 * Provides a union of two queries.
 * Emits matches from both queries in no particular order, or uniqueness guearantees
 * */
infix fun <T> Query<*, T>.union(rhs: Query<*, T>): Query<Many, T> =
  let { lhs ->
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
infix fun <T, U> Query<*, T>.product(rhs: Query<*, U>): Query<Many, Pair<T, U>> = let { lhs ->
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
fun <T, U> Query<*, T>.flatMap(f: (T) -> Set<U>): Query<Many, U> = let { query ->
  Query { flatMap(query.producer()) { match -> f(match.value) } }
}

/**
 * version of a [flatMap] working with [Match]
 * [f] has to be a *pure* function of a database
 * unlike values, [Match]es will be unique in some way
 * */
fun <T, U> Query<*, T>.flatMapMatch(f: (Match<T>) -> Set<U>): Query<Many, U> =
  let { query ->
    Query { flatMap(query.producer(), f) }
  }

/**
 * maps a database snapshot to a single value of [f]
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * */
@Suppress("UNCHECKED_CAST")
fun <T> query(f: () -> T): StateQuery<T> = queryOf(Unit).flatMap { setOf(f()) } as StateQuery<T>

/**
 * maps a database snapshot to a single value of [f] unless it returned null
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * */
@Suppress("UNCHECKED_CAST")
fun <T : Any> queryNotNull(f: () -> T?): Query<Maybe, T> = queryOf(Unit).flatMap { setOfNotNull(f()) } as Query<Maybe, T>

/**
 * maps a database snapshot to a set of values returned by [f], yielding them as matches
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * */
fun <T> queryMany(f: () -> Set<T>): Query<Many, T> = queryOf(Unit).flatMap { f() }

/**
 * yields Unit when predicate [p] is true
 * [p] is being read-tracked and recalculated whenever related data in the db changes
 * [p] has to be a *pure* function of a database
 * */
fun predicateQuery(p: () -> Boolean): Query<Maybe, Unit> = queryOf(Unit).filter { p() }

/**
 * [p] is applied to the values of incoming matches and propagates them forward if true,
 * [p] is being read-tracked
 * [p] has to be a *pure* function of a database
 * */
@JvmName("filterMaybe")
@Suppress("UNCHECKED_CAST")
fun <T> Query<Maybe, T>.filter(p: (T) -> Boolean): Query<Maybe, T> =
  (this as Query<*, T>).filter(p) as Query<Maybe, T>

fun <T> Query<*, T>.filter(p: (T) -> Boolean): Query<Many, T> =
  flatMap { t -> if (p(t)) setOf(t) else emptySet() }

/**
 * Returns a query containing only matches of the original query that are instances of specified type parameter [R].
 * */
@JvmName("filterIsInstanceMaybe")
@Suppress("UNCHECKED_CAST")
inline fun <reified R> Query<Maybe, *>.filterIsInstance(): Query<Maybe, R> =
  flatMap { t -> if (t is R) setOf(t) else emptySet() } as Query<Maybe, R>

inline fun <reified R> Query<*, *>.filterIsInstance(): Query<Many, R> =
  flatMap { t -> if (t is R) setOf(t) else emptySet() }

/**
 * returns first value of [Query] [Match]es
 * */
suspend fun <T> Query<*, T>.first(): T = asValuesFlow().first()

/**
 * returns first value of [Query] [Match]es that satisfies [p] to true,
 * [p] is being read-tracked
 * */
suspend fun <T> Query<*, T>.first(p: (T) -> Boolean): T = filter(p).first()

/**
 * version of [filter] working with [Match]
 * [p] has to be a *pure* function of a database
 * */
@JvmName("filterMatchMaybe")
@Suppress("UNCHECKED_CAST")
fun <T> Query<Maybe, T>.filterMatch(p: (Match<T>) -> Boolean): Query<Maybe, T> =
  (this as Query<*, T>).filterMatch(p) as Query<Maybe, T>

fun <T> Query<*, T>.filterMatch(p: (Match<T>) -> Boolean): Query<Many, T> =
  flatMapMatch { t -> if (p(t)) setOf(t.value) else emptySet() }

/**
 * maps values produced by query with a pure function [f]
 * for every match of query will produce a new match
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * may yield repeated values if [f] is not injective
 * unlike values, [Match]es will be unique in some way
 * */
@Suppress("UNCHECKED_CAST")
fun <C : Cardinality, T, U> Query<C, T>.map(f: (T) -> U): Query<C, U> = flatMap { t -> setOf(f(t)) } as Query<C, U>

/**
 * see [map]
 * produces match only if [f] returned non-null
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * */
fun <T, U> Query<*, T>.mapNotNull(f: (T) -> U?): Query<Many, U> =
  flatMap { t -> f(t)?.let(::setOf) ?: emptySet() }

@JvmName("mapNotNullMaybe")
@Suppress("UNCHECKED_CAST")
fun <T, U> Query<Maybe, T>.mapNotNull(f: (T) -> U?): Query<Maybe, U> =
  (this as Query<*, T>).mapNotNull(f) as Query<Maybe, U>

/**
 * Returns a query containing only matches of the original query that are not null.
 * */
fun <T> Query<*, T?>.filterNotNull(): Query<Many, T> = mapNotNull { it }

@JvmName("filterNotNullMaybe")
@Suppress("UNCHECKED_CAST")
fun <T> Query<Maybe, T?>.filterNotNull(): Query<Maybe, T> =
  (this as Query<*, T?>).filterNotNull() as Query<Maybe, T>

/**
 * Version of [map] working with [Match]
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * */
@Suppress("UNCHECKED_CAST")
fun <C : Cardinality, T, U> Query<C, T>.mapMatch(f: (Match<T>) -> U): Query<C, U> =
  flatMapMatch { t -> setOf(f(t)) } as Query<C, U>

/**
 * Yields Unit whenever receiver produces any match.
 * */
@Suppress("UNCHECKED_CAST")
fun <T> Query<*, T>.any(): Query<Maybe, Unit> = rawMap { }.distinct() as Query<Maybe, Unit>

/**
 * Query does not carry a distinct set of values by default for performance and memory reasons.
 * Sometimes one need to stop propagating duplicates further for performance reasons.
 * */
fun <C : Cardinality, T : Any> Query<C, T>.distinct(): Query<C, T> =
  let { query ->
    Query { distinct(query.producer()) }
  }

/**
 * Merges two queries together to provide pairs of values corresponding to arms, for which a certain value is equal, together with the coinciding value
 * [on] functions are allowed to read the db and will be reactive
 * */
infix fun <L, R, T> JoinHand<L, T>.join(rhs: JoinHand<R, T>): Query<Many, JoinPair<L, R, T>> =
  let { lhs ->
    joinOn(lhs.query, lhs.on, rhs.query, rhs.on)
  }

data class JoinPair<L, R, T>(
  val left: L,
  val right: R,
  val on: T,
)

data class JoinHand<T, U>(
  val query: Query<*, T>,
  val on: (Match<T>) -> U,
)

/**
 * Makes a [JoinHand] for use in [join]
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * */
fun <T, U> Query<*, T>.on(f: (T) -> U): JoinHand<T, U> =
  let { query ->
    JoinHand(query) { match -> f(match.value) }
  }

/**
 * version of [on] working with [Match]es
 * [f] is being read-tracked and recalculated whenever related data in the db changes
 * [f] has to be a *pure* function of a database
 * */
fun <T, U> Query<*, T>.onMatch(f: (Match<T>) -> U): JoinHand<T, U> =
  let { query ->
    JoinHand(query, f)
  }

/**
 * Emits a match for every value of an [attribute] of input [Query].
 * Similar to [Entity.get] but for [Query]
 * */
operator fun <C : Cardinality, E : Entity, T : Any> Query<C, E>.get(attribute: EntityAttribute<E, T>): Query<C, T> =
  rawMap { m -> m.value.eid }
    .getAttribute(attribute.attr)
    .rawMap { match -> attribute.fromIndexValue(match.value) }

/**
 * Similar to [Entity.lookup] but for [Query]
 * */
fun <E : Entity, T : Any> Query<*, T>.lookup(attribute: EntityAttribute<E, T>): Query<Many, E> =
  rawMap { attribute.toIndexValue(it.value) }
    .lookupAttribute(attribute.attr as Attribute<Any>)
    .rawMap { entity(it.value) as E }

/**
 * Column query for [EntityAttribute],
 * Similar to [EntityAttribute.all] but for [Query]
 * */
fun <E : Entity, T : Any> EntityAttribute<E, T>.each(): Query<Many, Pair<E, T>> =
  let { attribute ->
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
fun <C : Cardinality, T : Any> Query<C, EID>.getAttribute(attribute: Attribute<T>): Query<C, T> =
  let { query ->
    Query { getAttribute(query.producer(), attribute) }
  }

/**
 * Related to [lookup], but acts on a raw [Attribute] instead of [kotlin.reflect.KProperty]
 * */
fun <T : Any> Query<*, T>.lookupAttribute(attribute: Attribute<T>): Query<Many, EID> =
  let { query ->
    Query { lookupAttribute(query.producer(), attribute) }
  }

/**
 * Maps a query using a pure function [f].
 * [f] is not supposed to read db, as it will not be tracked and thus will not be reactive
 * [f] has to be a *pure* function
 * */
fun <C : Cardinality, T, U> Query<C, T>.rawMap(f: (Match<T>) -> U): Query<C, U> =
  let { query ->
    Query { query.producer().rawMap(f) }
  }

fun <T, U> Producer<T>.rawMap(f: (Match<T>) -> U): Producer<U> =
  let { producer ->
    producer.transform { token, emit ->
      emit(Token(token.added, token.match.withValue(f(token.match))))
    }
  }

/**
 * filter matches of query with [p]
 * [p] is not read-tracked and is not supposed to read the db
 * [p] has to be a *pure* function
 * */
fun <T> Query<*, T>.rawFilter(p: (Match<T>) -> Boolean): Query<Many, T> =
  let { query ->
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
fun <T, U> Query<*, T>.transform(f: (Token<T>, Collector<U>) -> Unit): Query<Many, U> = let { query ->
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
fun <Acc, T> Query<*, T>.reductions(acc: Acc, f: (Acc, Token<T>) -> Acc): StateQuery<Acc> =
  let { query ->
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
fun Query<Many, Int>.sum(): StateQuery<Int> =
  reductions(0) { acc, token ->
    when (token.added) {
      true -> acc + token.value
      false -> acc - token.value
    }
  }

/**
 * Runs [Query] just one time, to produce a [Set] of it's matches.
 * */
fun <T> Query<Many, T>.matches(): Set<T> =
  let { query ->
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
 * For example, given Query<FooEntity>, we may [fleet.kernel.rete.impl.getAttribute] on it, yielding Query<Foo>.
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
fun <T> Query<*, T>.bind(): BoundQuery<T> =
  let { query ->
    val key = Any()
    BoundQuery(key, query.transform { token, emit ->
      emit(Token(token.added, token.match.bind(key)))
    })
  }

data class BoundQuery<T>(val key: Any, val query: Query<Many, T>) : Query<Many, T> by query

/**
 * [Producer]s of interned queries are shared between their dependant queries, allowing to share the work and save memory.
 * It makes sense to [intern] query if it is used to construct more than one other query.
 * */
fun <C : Cardinality, T> Query<C, T>.intern(firstKey: Any, vararg keys: Any): Query<C, T> =
  internImpl(listOf(firstKey, *keys))

@PublishedApi
internal fun <C : Cardinality, T> Query<C, T>.internImpl(key: Any): Query<C, T> =
  InternedQuery(key, this)

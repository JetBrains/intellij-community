// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete

import com.jetbrains.rhizomedb.*
import fleet.kernel.rete.impl.*
import it.unimi.dsi.fastutil.longs.LongSet
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [Token] is a [Match] with a boolean, indicating whether the [Match] is being asserted or retracted
 * */
data class Token<out T>(val added: Boolean, val match: Match<T>) {
  val value: T get() = match.value
}

/**
 * [Query] is a stateful function, which is supposed to initialize it's state (memory) and return a [Producer]
 * One may think of it as a reducer
 * [Producer] is invoked for every new subscriber, which could be another [Query] or [QueryObserver]
 * It must provide [Collector] with accumulated set of matches, and call it again with any new ones
 * */
fun interface Query<out T> {
  fun QueryScope.producerImpl(): Producer<T>
}

/**
 * [Broadcaster] is an interface, which is there to help [Query] to keep track of all of it's [Collector]s
 * When called with a [Match], it will broadcast it to all [Collector]s for which [collect] is called
 * To instantiate one, use [Broadcaster] function
 * It is useful to have one created in [Query] to share the work with all [Collector]s by subscribing to patterns or other [Producer]s
 * */
interface Broadcaster<T> : Collector<T>, Producer<T>

/**
 * Instantiated [Query]
 * See [Query]
 * */
fun interface Producer<out T> {
  fun SubscriptionScope.collectImpl(emit: Collector<T>)
}

/**
 * Consumer of [Match]es
 *
 * */
fun interface Collector<in T> {
  operator fun invoke(token: Token<T>)
}

@DslMarker
annotation class ReteDsl


/**
 * [QueryScope] is a [SubscriptionScope] which allows [Query] to subscribe to other [Query]s
 * We need to know dependencies of a [Query] upfront in order to control in which order they can be fed with [Token]s
 * */
@ReteDsl
interface QueryScope : SubscriptionScope {
  fun <T> Query<T>.producer(): Producer<T>
}

/**
 * [Query] has a capabilities to subscribe to db changes and [Producer]s
 * While it is possible to subscribe to [Producer] for every subscriber independently,
 * it is useful to do it only once to share the costly work between all [Collector]s
 * */
@ReteDsl
interface SubscriptionScope {
  /**
   * Registers callback on disposal of a scope,
   * it being a [Query] lifetime, or a specific [Collector]
   * */
  fun onDispose(sub: Subscription)

  /**
   * Subscribes to a specific [Datom] mask
   * Only those [Datom] matching the mast will be presented to [DatomPort] in no specific order
   * */
  fun subscribe(e: EID?, attribute: Attribute<*>?, v: Any?, datomPort: DatomPort)

  /**
   * Subscribes to a set of pattern hashes
   * [RevalidationPort] will be invoked no more than once per [Change]
   * */
  fun subscribe(patterns: LongSet, port: RevalidationPort)

  /**
   * Subscribes a [Collector] to a [Producer] with the lifetime of the [SubscriptionScope]
   * */
  fun <T> Producer<T>.collect(emit: Collector<T>)

  /**
   * Creates a child [SubscriptionScope], returning a handle which can be used to terminate all of the child's subscriptions
   * */
  fun scope(body: SubscriptionScope.() -> Unit): Subscription
}

/**
 * See [SubscriptionScope]
 * */
fun interface DatomPort {
  fun feedDatom(datom: EAVa)
}

/**
 * See [SubscriptionScope]
 * */
fun interface RevalidationPort {
  fun revalidate(): LongSet
}

/**
 * Closable handle used in the lib
 * */
fun interface Subscription {
  fun close()
}

/**
 * Callback, called with subsequent [TokenSet]s of [QueryObserver]
 * [TokenSet] corresponds to a reaction to a single [Change], if any
 * */
fun interface OnTokens<in T> {
  companion object {
    private val Noop = OnTokens<Any> { }
    fun <T> noop(): OnTokens<T> = Noop as OnTokens<T>
  }

  fun tokens(tokens: TokenSet<T>)
}

/**
 * Given a set of initial matches should return a callback to be called for any subsequent ones
 * */
fun interface QueryObserver<in T> {
  fun SubscriptionScope.init(initialMatches: Set<Match<T>>): OnTokens<T>
}

/**
 * Internal interface to interact with [ReteNetwork]
 * Mutable state of Rete network, intended to be used from a single thread or coroutine, no thread-safety guearantees
 * */
internal interface ReteNetwork {
  companion object {
    fun new(dbState: MutableStateFlow<ReteState>, failWhenPropagationFailed: Boolean): ReteNetwork =
      ReteNetworkImpl(dbState, failWhenPropagationFailed)
  }

  fun <T> observeQuery(
    query: Query<T>,
    tracingKey: QueryTracingKey?,
    dependencies: Collection<ObservableMatch<*>>,
    observer: QueryObserver<T>
  ): Subscription

  fun propagateChange(change: Change)
}

/**
 * Returns a [Broadcaster], the implementation calls it's subscribers only with subsequent [Token]s,
 * has no memory except the set of [Collector]s
 * */
fun <T> Broadcaster(): Broadcaster<T> = BroadcasterImpl()

/**
 * Represents a set of asserted and retracted tokens produced by a single [Change]
 * */
data class TokenSet<out T>(val asserted: Set<Match<T>>,
                           val retracted: Set<Match<T>>) : Iterable<Token<T>> {
  override fun iterator(): Iterator<Token<T>> =
    iterator {
      retracted.forEach { m ->
        yield(Token(false, m))
      }
      asserted.forEach { m ->
        yield(Token(true, m))
      }
    }
}

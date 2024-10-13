// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete

import com.jetbrains.rhizomedb.*
import fleet.kernel.rete.SubscriptionsIndex.PatternIndexEntry.DatomEntry
import fleet.kernel.rete.SubscriptionsIndex.PatternIndexEntry.RevalidationEntry
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongSet
import kotlinx.coroutines.flow.MutableStateFlow
import fleet.util.PriorityQueue

internal class IdGen(private val observerId: Int) {
  private var nextQueryId: Int = 1

  fun nextQueryId(): NodeId = NodeId(observerId, nextQueryId++)
}

internal val Long.highInt: Int get() = shr(32).toInt()
internal val Long.lowInt: Int get() = shl(32).shr(32).toInt()

@JvmInline
internal value class NodeId private constructor(val id: Long) {
  constructor(observerId: Int, queryId: Int) : this(observerId.toLong().shl(32) or queryId.toLong())

  val queryId: Int get() = id.lowInt
  val observerId: Int get() = id.highInt
}

internal fun SubscriptionTree.subscriptionScope(
  node: Node,
  subscriptionIndex: SubscriptionsIndex,
  depth: Int,
  tracingKey: QueryTracingKey?
): SubscriptionScope = let { scope ->
  object : SubscriptionScope {
    override fun onDispose(sub: Subscription) = scope.attach(sub)

    override fun subscribe(e: EID?, attribute: Attribute<*>?, v: Any?, datomPort: DatomPort) =
      scope.attach(
        subscriptionIndex.sub(
          DatomEntry(
            node = node,
            eid = e,
            attribute = attribute,
            value = v,
            depth = depth,
            port = datomPort.tracing(e, attribute, v, tracingKey)
          ),
          LongSet.of(Pattern.pattern(e, attribute, v).hash)))

    override fun subscribe(patterns: LongSet, port: RevalidationPort) =
      scope.attach(subscriptionIndex.sub(
        RevalidationEntry(
          node = node,
          depth = depth,
          port = port.tracing(patterns, tracingKey)
        ),
        patterns))

    override fun <T> Producer<T>.collect(emit: Collector<T>) =
      collectImpl(emit)

    override fun scope(body: SubscriptionScope.() -> Unit): Subscription =
      attachTree().also { child ->
        child.subscriptionScope(node, subscriptionIndex, depth, tracingKey).body()
      }
  }
}

internal class ProducerNode(
  val node: Node,
  val producer: Producer<*>
)

internal class ObserverNode(
  val node: Node,
  var onTokens: OnTokens<*>
) {

  private var dependants: MutableMap<Match<*>, MutableSet<Node>>? = null

  fun getDependants(match: Match<*>): Set<Node>? =
    dependants?.get(match)

  fun removeDependant(match: Match<*>, node: Node) {
    dependants?.let { matches ->
      matches.removeIf(match) { nodes ->
        nodes.remove(node)
        nodes.isEmpty()
      }
      if (matches.isEmpty()) {
        dependants = null
      }
    }
  }

  fun addDependant(match: Match<*>, node: Node) {
    when (val dependants = dependants) {
      null -> this.dependants = adaptiveMapOf(match, adaptiveSetOf(node))
      else -> dependants.getOrPut(match) { adaptiveSetOf() }.add(node)
    }
  }
}

internal class Node(val nodeId: NodeId) {

  var retainers: MutableSet<Node>? = null
  private var retainees: ArrayList<Node>? = null
  val subscriptionTree = SubscriptionTree(null).also {
    it.attach {
      retainees?.toList()?.forEach { retainee ->
        retainee.removeRetainer(this)
      }
    }
  }

  private fun removeRetainer(node: Node) {
    val retainers = requireNotNull(retainers) { "removing retainer from empty set" }
    require(retainers.remove(node)) { "removing retainer which is not there" }
    if (retainers.isEmpty()) {
      subscriptionTree.close()
    }
  }

  fun addRetainer(node: Node) {
    when (val retainers = retainers) {
      null -> this.retainers = adaptiveSetOf(node)
      else -> retainers.add(node)
    }
  }

  fun addRetainee(node: Node) {
    when (val retainees = retainees) {
      null -> this.retainees = arrayListOf(node)
      else -> retainees.add(node)
    }
  }
}

internal class PropagationTask(val node: Node) {

  val noveltyForDatomEntry: HashMap<DatomEntry, ArrayList<EAVa>> = HashMap()

  /** initially populated by triggered pattern-subscriptions,
   * when processing tokens from the upstream, we need to remove retractions from here
   * */
  val revalidationEntries: HashSet<RevalidationEntry> = HashSet()

  /** tokens grouped by the port, where they has to be fed to
   * this map is populated by products of upstream nodes when propagating the Change
   * */
  val tokensForCollector: HashMap<Collector<*>, MutableTokenSet> = HashMap()

  fun send(token: Token<*>, input: Collector<*>) {
    tokensForCollector.getOrPut(input, ::MutableTokenSet).add(token)
  }
}

internal class Propagation {

  val observerTokens = HashMap<Node, MutableTokenSet>()
  val failedNodes = HashMap<Node, Throwable>()

  // novel tokens and datoms to be fed to the node when the time comes for it's processing
  val tasks = HashMap<Node, PropagationTask>()

  // dirty nodes, ordered by the time of creation, which is consistent with the order of dependencies and inputs
  private val q = PriorityQueue<Node>(Comparator { lhs, rhs ->
    when (val res = lhs.nodeId.observerId.compareTo(rhs.nodeId.observerId)) {
      0 -> -lhs.nodeId.queryId.compareTo(rhs.nodeId.queryId)
      else -> res
    }
  })

  fun isEmpty(): Boolean = tasks.isEmpty()

  fun initTask(node: Node): PropagationTask =
    tasks.getOrPut(node) {
      q.add(node)
      PropagationTask(node)
    }

  fun processQueue(body: (PropagationTask) -> Unit) {
    while (true) {
      q.poll()?.let { nodeId ->
        body(tasks.remove(nodeId)!!)
      } ?: break
    }
    require(tasks.isEmpty())
  }
}

internal class ReteNetworkImpl(
  val lastKnownDb: MutableStateFlow<ReteState>,
  val failWhenPropagationFailed: Boolean
) : ReteNetwork {

  val subscriptionIndex = SubscriptionsIndex()
  val producers = HashMap<InternedQuery<*>, ProducerNode>()
  val observers = Long2ObjectOpenHashMap<ObserverNode>()
  var propagation: Propagation? = null
  var nextObserverId: Int = 0
  var hydrating: Boolean = false

  fun <T> safeProducer(query: Query<T>, scope: QueryScope, node: Node): Producer<T> =
    query.runCatching {
      scope.producerImpl().catching { x ->
        Rete.logger.error(x, "producer failed for: $query")
        propagation!!.failedNodes[node] = x
      }
    }.onFailure { x ->
      propagation!!.failedNodes[node] = x
      Rete.logger.error(x, "query failed to provide producer: $query")
    }.getOrElse { x ->
      Producer {
        propagation!!.failedNodes[node] = x
        Rete.logger.error(x, "producer failed for: $query")
      }
    }

  fun internQuery(query: InternedQuery<*>, nodeBuilder: () -> ProducerNode): ProducerNode =
    producers.getOrPut(query) {
      nodeBuilder().also {
        it.node.subscriptionTree.attach {
          producers.remove(query)
        }
      }
    }

  fun addObserver(observerNode: ObserverNode, dependencies: Collection<ObservableMatch<*>>) {
    observers[observerNode.node.nodeId.id] = observerNode
    val deps = dependencies.map { match ->
      observers[match.observerId.id]!! to match
    }
    deps.forEach { (dependencyNode, match) ->
      dependencyNode.addDependant(match, observerNode.node)
    }
    observerNode.node.subscriptionTree.attach {
      observers.remove(observerNode.node.nodeId.id)
      deps.forEach { (dependencyNode, match) ->
        dependencyNode.removeDependant(match, observerNode.node)
      }
    }
  }

  fun retain(node: Node, retainer: Node) {
    node.addRetainer(retainer)
    retainer.addRetainee(node)
  }

  internal fun queryScope(node: Node, depth: Int, idGen: IdGen, tracingKey: QueryTracingKey?): QueryScope =
    object : QueryScope, SubscriptionScope by node.subscriptionTree.subscriptionScope(node, subscriptionIndex, depth, tracingKey) {
      /**
       * Enforce hierarchical order of revalidation entries
       * There was nothing in place to enforce the evaluation order of `filter{}.map{}`.
       * Since they are fused within the same network Node, subscriptions to patterns are not ordered in any way.
       * However if the upstream would retract the match, the pattern subscription of the downstream, corresponding to this match should have been discarded
       * */
      override fun <T> Query<T>.producer(): Producer<T> = let { query ->
        when {
          query is InternedQuery<*> -> {
            val producerNode = internQuery(query) {
              val producerNode = Node(idGen.nextQueryId())
              val queryScope = queryScope(producerNode, 0, idGen, tracingKey)
              val producer = safeProducer(query, queryScope, producerNode)
              ProducerNode(producerNode, producer).also {
                Rete.logger.trace { "Inserted query $query as node ${it}" }
              }
            }

            @Suppress("UNCHECKED_CAST")
            val producer = producerNode.producer as Producer<T>
            retain(producerNode.node, node)
            Producer { emit ->
              producer.collect { token ->
                when {
                  hydrating -> emit(token)
                  else -> propagation!!.initTask(node).send(token, emit)
                }
              }
            }
          }
          else ->
            queryScope(node, depth + 1, idGen, tracingKey).run {
              query.run { producerImpl() }
            }
        }
      }
    }

  override fun <T> observeQuery(
    query: Query<T>,
    tracingKey: QueryTracingKey?,
    dependencies: Collection<ObservableMatch<*>>,
    observer: QueryObserver<T>
  ): Subscription =
    if (dependencies.all { match -> match.invalidationTs.isActive }) {
      val db = lastKnownDb.value.dbOrThrow()
      val observerId = nextObserverId++
      val observerIdPair = NodeId(observerId, 0)
      require(observerIdPair.id !in observers.keys) { "observer id is not unique" }
      val observerNode = Node(observerIdPair)
      val queryScope = queryScope(observerNode, 0, IdGen(observerId), tracingKey)
      val prop = Propagation()
      propagation = prop
      hydrating = true
      val observableQuery = query
        .observable(observerIdPair)
        .tracing(tracingKey)
      val producer = asOf(db) {
        safeProducer(observableQuery, queryScope, observerNode)
      }
      Rete.logger.trace { "Inserted query $query as node ${observerNode}" }
      queryScope.run {
        asOf(db) {
          producer.collect { token ->
            if (!token.added) {
              val dependants = observers[observerIdPair.id]!!.getDependants(token.match)?.toList()
              dependants?.forEach { it.subscriptionTree.close() }
            }
            propagation!!.observerTokens.getOrPut(observerNode, ::MutableTokenSet).add(token)
          }
        }
      }
      val failures = prop.failedNodes.values.map { x -> Match.failure<Any?>(x) }.toSet()
      val initialMatches = failures + (prop.observerTokens[observerNode]?.asserted ?: emptySet())
      val onTokens = runCatching {
        asOf(db) {
          observer.run {
            queryScope.run {
              @Suppress("UNCHECKED_CAST")
              init(initialMatches as Set<Match<T>>)
            }
          }
        }
      }.getOrElse { x ->
        Rete.logger.error(x, "$observer failed with initial matches $initialMatches")
        OnTokens.noop()
      }
      addObserver(ObserverNode(observerNode, onTokens), dependencies)
      hydrating = false
      propagation = null
      observerNode.subscriptionTree
    }
    else {
      Subscription { }
    }

  override fun propagateChange(change: Change) {
    val prop = Propagation()
    // build initial working set from novelty:
    change.novelty.deduplicateValues().forEach { datom ->
      subscriptionIndex.query(datom.eid, datom.attr, datom.value).forEach { entry ->
        val task = prop.initTask(entry.node)
        when (entry) {
          is DatomEntry -> {
            task.noveltyForDatomEntry.getOrPut(entry) { ArrayList() }.add(datom)
          }
          is RevalidationEntry -> {
            task.revalidationEntries.add(entry)
          }
        }
      }
    }

    // propagate novelty and tokens:
    propagation = prop
    val context = DbContext.threadBound
    prop.processQueue { task ->
      if (!task.node.subscriptionTree.isClosed()) {
        runCatching {
          // process tokens first, the order is important, as queries are free to rely on it (see Lookup)
          task.tokensForCollector.forEach { (emit, tokens) ->
            tokens.forEach { token ->
              context.alter(if (token.added) change.dbAfter else change.dbBefore) {
                @Suppress("UNCHECKED_CAST")
                (emit as Collector<Any?>)(token as Token<Any?>)
              }
            }
          }

          task.noveltyForDatomEntry.entries.sortedByDescending { it.key.depth }.forEach { (datomEntry, datoms) ->
            datoms.forEach { datom ->
              context.alter(if (datom.added) change.dbAfter else change.dbBefore) {
                datomEntry.port.feedDatom(datom)
              }
            }
          }

          task.revalidationEntries.sortedByDescending { it.depth }.forEach { entry ->
            val newPatterns = context.alter(change.dbAfter) {
              entry.port.revalidate()
            }
            subscriptionIndex.updatePatterns(entry, newPatterns)
          }
        }.onFailure { x ->
          if (failWhenPropagationFailed) {
            throw x
          }
          prop.failedNodes[task.node] = x
          Rete.logger.error(x) { "propagation to ${task.node} has failed" }
        }
      }
    }

    commit(change, prop)
    propagation = null
  }

  fun propagateFailures(node: Node, x: Throwable) {
    if (observers[node.nodeId.id] != null) {
      propagation!!.observerTokens.getOrPut(node, ::MutableTokenSet).add(Token<Any?>(true, Match.failure(x)))
    }
    node.retainers?.forEach { retainer ->
      propagateFailures(retainer, x)
    }
  }

  fun commit(change: Change, prop: Propagation) {
    // propagate fake Failure tokens:
    run {
      prop.failedNodes.forEach { (node, x) ->
        propagateFailures(node, x)
      }
      prop.failedNodes.clear()
    }

    lastKnownDb.value = ReteState.Db(change.dbAfter)

    // notify assertions:
    run {
      prop.observerTokens.forEach { (node, ts) ->
        val terminal = observers[node.nodeId.id]!!
        runCatching {
          terminal.onTokens.tokens(ts.asTokenSet())
        }.onFailure { x ->
          Rete.logger.error(x, "terminal $terminal failed with tokens ${ts.asserted}")
        }
      }
    }
  }
}

fun <T> Producer<T>.catching(handler: (Throwable) -> Unit): Producer<T> =
  Producer { emit ->
    runCatching {
      collect(emit)
    }.onFailure(handler)
  }

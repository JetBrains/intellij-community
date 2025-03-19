// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.impl.EidGen
import com.jetbrains.rhizomedb.impl.generateSeed
import fleet.kernel.*
import fleet.kernel.rebase.RebaseLogger.logger
import fleet.rpc.client.RpcClientDisconnectedException
import fleet.rpc.client.durable
import fleet.reporting.shared.tracing.span
import fleet.reporting.shared.tracing.spannedScope
import fleet.util.*
import fleet.util.async.conflateReduce
import fleet.util.async.use
import fleet.util.channels.channels
import fleet.util.channels.use
import fleet.fastutil.ints.IntMap
import fleet.fastutil.ints.partition
import fleet.multiplatform.shims.AtomicRef
import fleet.util.logging.logger
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.whileSelect
import kotlinx.serialization.builtins.serializer

suspend fun withRebaseLoop(
  remoteKernel: RemoteKernel,
  instructionSet: InstructionSet,
  reconnectWhenBroken: Boolean,
  body: suspend CoroutineScope.() -> Unit,
) {
  spannedScope("withRemoteKernel") {
    val ready = CompletableDeferred<Unit>()
    launch {
      val rebaseLoop: suspend CoroutineScope.() -> Unit = {
        durable {
          remoteKernelConnection(ready, remoteKernel, instructionSet)
        }
      }
      if (reconnectWhenBroken) {
        retryRebaseLoop(rebaseLoop)
      }
      else {
        rebaseLoop()
      }
    }.use { rebaseLoop ->
      rebaseLoop.invokeOnCompletion { c ->
        if (c !is CancellationException) {
          logger.error(c) { "rebase loop finished abnormally" }
        }
        else {
          logger.info("rebase loop exit")
        }
      }
      ready.await()
      body()
    }
  }
}

internal typealias Timestamp = Long

internal data class SpeculationData(
  val novelty: Novelty,
  val idMappings: List<IntMap<UID>>,
) {
  companion object {
    fun empty(): SpeculationData {
      return SpeculationData(Novelty.Empty, emptyList())
    }
  }
}

private object CommittedTransactionsKey : ChangeScopeKey<List<Transaction>>
internal object RebaseLogEntryKey : ChangeScopeKey<RebaseLogEntry>

internal data class RebaseLoopState(
  val rebaseLog: RebaseLog,
  val timestamp: Timestamp,
  val committedEffectsAndNovelty: EffectsAndNovelty,
  val baseClock: VectorClock,
) {
  companion object {
    fun initial(initialDB: DB, timestamp: Timestamp, baseClock: VectorClock): RebaseLoopState {
      return RebaseLoopState(rebaseLog = RebaseLog.empty(initialDB.selectPartitions(setOf(SharedPart))),
                             timestamp = timestamp,
                             committedEffectsAndNovelty = EffectsAndNovelty.empty(),
                             baseClock = baseClock)
    }
  }
}

internal object RebaseLogger {
  val logger = logger<RebaseLogger>()
}

private suspend fun offering(transactor: Transactor, offersChan: ReceiveChannel<RebaseLoopState>) {
  var delayedEffectsAndNovelty = EffectsAndNovelty.empty()
  offersChan.consumeAsFlow()
    .conflateReduce { rebaseLoopState, rebaseLoopState2 ->
      rebaseLoopState2.copy(
        committedEffectsAndNovelty = rebaseLoopState.committedEffectsAndNovelty + rebaseLoopState2.committedEffectsAndNovelty)
    }
    .collect { state ->
      delayedEffectsAndNovelty += state.committedEffectsAndNovelty
      try {
        transactor.changeSuspend {
          state.rebaseLog.invariant(!state.rebaseLog.isRebasing()) { "trying to offer in rebasing state" }
          val remoteKernelConnectionEntity = RemoteKernelConnectionEntity.current!!
          val kernelTimestamp = remoteKernelConnectionEntity.sharedPartitionTimestamp
          if (kernelTimestamp == state.timestamp) {
            val newSpeculativeNovelty = state.rebaseLog.speculation.entries.flatMap { e -> e.sharedNovelty }.toNovelty()
            val oldSpeculativeNovelty = remoteKernelConnectionEntity.speculationData.novelty
            val effectiveCumulativeNovelty = -oldSpeculativeNovelty + delayedEffectsAndNovelty.novelty + newSpeculativeNovelty
            meta[MutableNoveltyKey]!!.addAll(effectiveCumulativeNovelty)
            val offerDB = state.rebaseLog.rebasing.base

            context.run {

              val schemaContribution = effectiveCumulativeNovelty.filter { sharedDatom ->
                partition(sharedDatom.eid) == SchemaPart && sharedDatom.added
              }

              val sharedRetractedEntities = effectiveCumulativeNovelty.mapNotNull { sharedDatom ->
                sharedDatom.eid.takeIf {
                  partition(sharedDatom.eid) == SharedPart &&
                  sharedDatom.attr == Entity.Type.attr &&
                  !sharedDatom.added &&
                  !offerDB.index.entityExists(sharedDatom.eid)
                }
              }

              val localRefsToRetractedEntities = sharedRetractedEntities
                .flatMap { eid -> queryIndex(IndexQuery.RefsTo(eid)) }
                .filter { datom -> partition(datom.eid) == FrontendPart }

              val (datomsToCascadeDelete, datomsToRetract) = localRefsToRetractedEntities.partition { datom ->
                datom.attr.schema.required || datom.attr.schema.cascadeDeleteBy
              }

              val entitiesToTRetract = datomsToCascadeDelete.flatMap { datom ->
                val (shared, local) = impl.entitiesToRetract(datom.eid).partition { partition(it) == SharedPart }
                shared.filter { !offerDB.index.entityExists(it) } + local
              }
              // these will produce new shared transaction:
              val sharedEntitiesToRetract = entitiesToTRetract.filter { partition(it) == SharedPart }

              // expand these against db before partition-swap, to capture correct values of onRetract effects

              val localRetractionsExpansion = generateSeed().let { seed ->
                AtomicComposite(
                  instructions = entitiesToTRetract
                    .filter { partition(it) != SharedPart }
                    .map { RetractEntityInPartition(it, seed) },
                  seed = seed,
                ).run { expand() }
              }

              val localPartitionAdjustment = Instruction.Const(
                seed = generateSeed(),
                effects = localRetractionsExpansion.effects,
                result = localRetractionsExpansion.ops +
                         schemaContribution.map { Op.Assert(it.eid, it.attr, it.value) } +
                         datomsToRetract.map { Op.Retract(it.eid, it.attr, it.value) }
              )

              impl.mutableDb.mergePartitionsFrom(offerDB)

              impl.mutableDb.run {
                queryCache = queryCache.invalidate(effectiveCumulativeNovelty)
              }

              context.mutate(localPartitionAdjustment)

              sharedEntitiesToRetract.forEach { retractEntity(it) }


              // run effects:
              runEffects(delayedEffectsAndNovelty.effects)

              runOfferContributors(effectiveCumulativeNovelty)

              // set new speculative novelty from offer:
              remoteKernelConnectionEntity[RemoteKernelConnectionEntity.SpeculationDataAttr] = SpeculationData(
                novelty = newSpeculativeNovelty,
                idMappings = state.rebaseLog.speculativeIdMappings()
              )

              // tick clock:
              val newClock = state.rebaseLog.speculation.entries.fold(state.baseClock) { clock, e ->
                e.transaction?.origin?.let { origin ->
                  clock.tick(origin)
                } ?: clock
              }
              remoteKernelConnectionEntity[RemoteKernelConnectionEntity.ClientClockAttr] = remoteKernelConnectionEntity.clientClock.copy(vectorClock = newClock)

              // drop accumulated effects and novelty:
              delayedEffectsAndNovelty = EffectsAndNovelty.empty()
            }
          }
          else {
            logger.trace { "[$transactor] offer with timestamp ${state.timestamp} was rejected by kernel with timestamp $kernelTimestamp" }
          }
        }
      }
      catch (c: CancellationException) {
        throw c
      }
      catch (x: Throwable) {
        logger.error(x, "offer failed $state")
      }
    }
}

private fun ChangeScope.runEffects(list: List<Effect>) {
  context.run {
    for (e in list) {
      try {
        e.effect(this)
      }
      catch (x: Throwable) {
        logger.error(x, "failed running effect ${e::class} in offer")
      }
    }
  }
}

internal fun ChangeScope.runOfferContributors(effectiveCumulativeNovelty: Novelty) {
  if (effectiveCumulativeNovelty.isNotEmpty()) {
    offerContributors().forEach { c: OfferContributor ->
      try {
        with(c) { contribute(effectiveCumulativeNovelty) }
      }
      catch (e: Throwable) {
        logger.error(e, "offer contributor $c failed")
      }
    }
  }
}

private fun dbDiff(from: Q, to: Q): Novelty {
  val res = MutableNovelty()
  from.queryIndex(IndexQuery.All())
    .filter { (e, _, _) ->
      partition(e) != SchemaPart
    }.forEach { fromDatom ->
      if (!to.contains(fromDatom)) {
        res.add(fromDatom.copy(added = false))
      }
    }

  to.queryIndex(IndexQuery.All())
    .forEach { toDatom ->
      if (!from.contains(toDatom)) {
        res.add(toDatom)
      }
    }
  return res.persistent()
}

suspend fun retryRebaseLoop(f: suspend CoroutineScope.() -> Unit) {
  while (true) {
    try {
      coroutineScope(f)
    }
    catch (x: RebaseLoopInvariantBroken) {
      logger.error(x) { "will reconnect" }
    }
  }
}

data class ClientClock(
  val vectorClock: VectorClock,
  val clientId: UID,
) {
  companion object {
    fun initial(clientId: UID): ClientClock = ClientClock(VectorClock.Zero, clientId)
  }

  fun tick(): ClientClock = copy(vectorClock = vectorClock.tick(clientId))
  fun tick(origin: UID): ClientClock = copy(vectorClock = vectorClock.tick(origin))
  fun compressed(): CompressedVectorClock = vectorClock.compress(clientId)

  fun index(): Long = vectorClock.clock[clientId] ?: 0L
}

internal data class RemoteKernelConnectionEntity(override val eid: EID) : Entity {
  companion object : EntityType<RemoteKernelConnectionEntity>(RemoteKernelConnectionEntity::class, ::RemoteKernelConnectionEntity) {
    val current: RemoteKernelConnectionEntity? get() = RemoteKernelConnectionEntity.singleOrNull()
    val ClientClockAttr = requiredTransient<ClientClock>("clientClock")
    val SpeculationDataAttr = requiredTransient<SpeculationData>("speculationData")
    val SharedPartitionTimestampAttr = requiredValue("sharedPartitionTimestamp", Long.serializer())
  }

  val clientClock by ClientClockAttr
  val speculationData by SpeculationDataAttr
  val sharedPartitionTimestamp by SharedPartitionTimestampAttr
}

val DB.clientClock: ClientClock?
  get() = asOf(this) {
    WorkspaceClockEntity.singleOrNull()?.clock ?: RemoteKernelConnectionEntity.singleOrNull()?.clientClock
  }

private fun ChangeScope.newRemoteKernelConnection(clientId: UID): RemoteKernelConnectionEntity {
  val current = RemoteKernelConnectionEntity.singleOrNull()
  val sharedPartitionTimestamp = current?.sharedPartitionTimestamp ?: 0L
  current?.delete()
  return RemoteKernelConnectionEntity.new {
    it[RemoteKernelConnectionEntity.ClientClockAttr] = ClientClock(vectorClock = VectorClock.Zero,
                                                                   clientId = clientId)
    it[RemoteKernelConnectionEntity.SpeculationDataAttr] = SpeculationData.empty()
    it[RemoteKernelConnectionEntity.SharedPartitionTimestampAttr] = sharedPartitionTimestamp + 1
  }
}

private suspend fun remoteKernelConnection(
  connected: CompletableDeferred<Unit>,
  remoteKernel: RemoteKernel,
  instructionSet: InstructionSet,
) {
  spannedScope("remoteKernelConnection") {
    val clientId = UID.random()
    val kernel = transactor()
    logger.info { "[$kernel] connects to workspace as $clientId" }
    change {
      newRemoteKernelConnection(clientId)
    }
    kernel.subscribe(Channel.UNLIMITED) { dbSnapshot, changesReceiver ->
      connected.complete(Unit)
      val subscription = spannedScope("RemoteKernel.subscribe") {
        durable { withoutCausality { remoteKernel.subscribe(clientId) } }
      }
      val snapshot = spannedScope("fetch snapshot") {
        DurableSnapshot(subscription.snapshot.toFlow().toList())
      }
      logger.info { "[$kernel] received snapshot with VectorClock: ${subscription.vectorClock}" }
      logger.trace { "[$kernel] received snapshot $subscription" }
      val snapshotSharedDB = span("build db from snapshot") {
        dbSnapshot.selectPartitions(emptySet()).change {
          context.impl.mutableDb.initPartition(SharedPart)
          val eidMemoizer = Memoizer<EID>()
          context.applyWorkspaceSnapshot(snapshot) { uid ->
            dbSnapshot.lookupOne(uidAttribute(), uid) ?: eidMemoizer.memo(false, uid) { EidGen.freshEID(SharedPart) }
          }
        }.dbAfter
      }
      spannedScope("rebaseLoop outer") {
        subscription.txs.toFlow().produceIn(this).consume {
          val workspaceBroadcastReceiver = this
          val (frontendTxsSender, frontendTxsReceiver) = channels<Transaction>()
          try {
            withoutCausality { remoteKernel.transact(frontendTxsReceiver) }
            logger.trace { "[$kernel] launching rebase loop" }
            val initialRebaseState = run {
              val baseClock = VectorClock(subscription.vectorClock.toPersistentHashMap())
              val timestamp = asOf(dbSnapshot) { RemoteKernelConnectionEntity.current!!.sharedPartitionTimestamp }
              val committedEffectsAndNovelty = EffectsAndNovelty(effects = emptyList(),
                                                                 novelty = dbDiff(
                                                                   from = dbSnapshot.selectPartitions(setOf(SharedPart)),
                                                                   to = snapshotSharedDB))
              RebaseLoopState(rebaseLog = RebaseLog.empty(snapshotSharedDB),
                              timestamp = timestamp,
                              committedEffectsAndNovelty = committedEffectsAndNovelty,
                              baseClock = baseClock)
            }
            rebaseLoop(transactor = kernel,
                       initial = initialRebaseState,
                       remoteKernelBroadcastReceiver = workspaceBroadcastReceiver,
                       changesReceiver = changesReceiver,
                       frontendTxsSender = frontendTxsSender,
                       instructionSet = instructionSet)
          }
          finally {
            // workspace does not care if this coroutine is failed or cancelled,
            // interrupted stream of frontend transactions has no special meaning
            frontendTxsSender.close()
          }
        }
      }
    }
  }
}

internal object RebaseLoopStateDebugKernelMetaKey : KernelMetaKey<AtomicRef<RebaseLoopState?>>

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun rebaseLoop(
  transactor: Transactor,
  initial: RebaseLoopState,
  remoteKernelBroadcastReceiver: ReceiveChannel<RemoteKernel.Broadcast>,
  changesReceiver: ReceiveChannel<Change>,
  frontendTxsSender: SendChannel<Transaction>,
  instructionSet: InstructionSet,
) {
  val rebaseLoopStateDebug = transactor.meta.getOrInit(RebaseLoopStateDebugKernelMetaKey) { AtomicRef(initial) }
  rebaseLoopStateDebug.set(initial)
  val encoder = instructionSet.encoder()
  val decoder = instructionSet.decoder()
  spannedScope("rebaseLoop") {
    val (offersSender, offersReceiver) = channels<RebaseLoopState>(1000)
    offersSender.use {
      launch { offering(transactor, offersReceiver) }
      var state: RebaseLoopState = initial
      if (!state.rebaseLog.isRebasing()) {
        offersSender.send(state)
        state = state.copy(committedEffectsAndNovelty = EffectsAndNovelty.empty())
      }
      logger.trace { "[$transactor] rebase loop launched" }
      whileSelect {
        rebaseLoopStateDebug.set(state)
        remoteKernelBroadcastReceiver.onReceiveCatching { broadcastOrClosed ->
          spannedScope("broadcast") {
            if (broadcastOrClosed.isClosed) {
              val closeCause = broadcastOrClosed.exceptionOrNull()
              val unconfirmed = unconfirmedInstructions(state)
              if (unconfirmed.isNotEmpty()) {
                logger.warn { "[$transactor] broadcast channel has closed, unconfirmed transactions: \n $unconfirmed" }
              }

              if (closeCause == null) {
                logger.info { "[$transactor] broadcast channel has closed" }
                false
              }
              else {
                logger.info { "[$transactor] broadcast channel has closed with exception: ${closeCause}" }
                logger.trace(closeCause) { "[$transactor] broadcast channel has closed with exception" }
                throw closeCause
              }
            }
            else {
              logger.trace { "[$transactor](${stateStr(state)}) received broadcast ${broadcastOrClosed.getOrThrow()}" }
              val broadcast = broadcastOrClosed.getOrThrow()
              state = state.consumeBroadcast(broadcast, decoder)

              if (!state.rebaseLog.isRebasing()) {
                offersSender.send(state)
                state = state.copy(committedEffectsAndNovelty = EffectsAndNovelty.empty())
              }
              true
            }
          }
        }
        changesReceiver.onReceiveCatching { changeOrClosed ->
          spannedScope("change") {
            if (changeOrClosed.isClosed) {
              val closeCause = changeOrClosed.exceptionOrNull()
              if (closeCause == null) {
                logger.trace { "[$transactor] subscription closed" }
              }
              else {
                logger.trace(closeCause) { "[$transactor] subscription closed with exception" }
                throw closeCause
              }
              false
            }
            else {
              val change = changeOrClosed.getOrThrow()
              val rebaseLogEntry = change.meta[RebaseLogEntryKey]
              if (rebaseLogEntry != null) {
                val changeTimestamp = asOf(change.dbAfter) { RemoteKernelConnectionEntity.current!!.sharedPartitionTimestamp }
                logger.trace { "[$transactor](${stateStr(state)}) rebase loop received change (ts=$changeTimestamp) $rebaseLogEntry" }
                val (newRebaseLog, localChanges) = state.rebaseLog
                  .append(rebaseLogEntry)
                  .skipLocalChanges()
                val effectsFromLocals = localChanges.flatMap(RebaseLogEntry::effects)
                val noveltyFromLocals = localChanges.flatMap { e -> e.sharedNovelty }.toNovelty()
                state = state.copy(rebaseLog = newRebaseLog,
                                   timestamp = changeTimestamp,
                                   committedEffectsAndNovelty = state.committedEffectsAndNovelty +
                                                                EffectsAndNovelty(effectsFromLocals, noveltyFromLocals))
                if (!state.rebaseLog.isRebasing()) {
                  offersSender.send(state)
                  state = state.copy(committedEffectsAndNovelty = EffectsAndNovelty.empty())
                }
              }
              true
            }
          }
        }
        if (state.rebaseLog.isRebasing()) {
          onTimeout(0) {
            spannedScope("rebase step") {
              val (rebaseLog, tx) = state.rebaseLog.continueRebase(encoder)
              state = state.copy(rebaseLog = rebaseLog)
              if (!state.rebaseLog.isRebasing()) {
                offersSender.send(state)
                state = state.copy(committedEffectsAndNovelty = EffectsAndNovelty.empty())
              }
              if (tx != null) {
                logger.trace { "[$transactor] sends tx: $tx" }
                frontendTxsSender.send(tx)
              }
              true
            }
          }
        }
      }
    }
  }
}

private fun addCommittedTxEffect(tx: Transaction): Effect = run {
  val effect: DbContext<Mut>.() -> Unit = {
    impl.meta.update(CommittedTransactionsKey) { txs ->
      (txs ?: emptyList()) + tx
    }
  }
  InstructionEffect(EffectInstruction(effect), effect)
}

private fun RebaseLoopState.consumeBroadcast(
  broadcast: RemoteKernel.Broadcast,
  decoder: InstructionDecoder,
): RebaseLoopState {
  return when (broadcast) {
    is RemoteKernel.Broadcast.Tx -> {
      val (rebaseLog, effectsAndNovelty) = rebaseLog.consumeTx(broadcast.transaction, decoder)
      copy(rebaseLog = rebaseLog,
           baseClock = baseClock.tick(broadcast.transaction.origin),
           committedEffectsAndNovelty = this.committedEffectsAndNovelty + effectsAndNovelty + EffectsAndNovelty(
             listOf(addCommittedTxEffect(broadcast.transaction)), Novelty.Empty))
    }
    is RemoteKernel.Broadcast.Ack -> {
      val (rebaseLog, effectsAndNovelty, tx) = rebaseLog.ack(txId = broadcast.transactionId, failed = false)
      copy(rebaseLog = rebaseLog,
           baseClock = baseClock.tick(tx.origin),
           committedEffectsAndNovelty = this.committedEffectsAndNovelty + effectsAndNovelty + EffectsAndNovelty(
             listOf(addCommittedTxEffect(tx)), Novelty.Empty))
    }
    is RemoteKernel.Broadcast.Failure -> {
      val maybeUnack = rebaseLog.firstOrNull()
      when {
        // it is our transaction that has failed, shame on us:
        maybeUnack?.transaction?.id == broadcast.transactionId -> {
          val (rebaseLog, effectsAndNovelty, tx) = rebaseLog.ack(txId = broadcast.transactionId, failed = true)
          val (_, novelty) = effectsAndNovelty
          val effectsAndNovelty1 = EffectsAndNovelty(listOf(addCommittedTxEffect(tx)), novelty)
          copy(rebaseLog = rebaseLog,
               baseClock = baseClock.tick(tx.origin),
               committedEffectsAndNovelty = this.committedEffectsAndNovelty + effectsAndNovelty1)
        }
        // the failure is not ours, just tick vector clock by imaginary empty transaction:
        else -> {
          val tx = Transaction(id = broadcast.transactionId,
                               instructions = emptyList(),
                               origin = broadcast.origin,
                               index = -1L)
          val effectsAndNovelty = EffectsAndNovelty(listOf(addCommittedTxEffect(tx)), Novelty.Empty)
          copy(committedEffectsAndNovelty = this.committedEffectsAndNovelty + effectsAndNovelty,
               baseClock = baseClock.tick(broadcast.origin))
        }
      }
    }
    is RemoteKernel.Broadcast.Rejection -> {
      val maybeUnack = rebaseLog.speculation.entries.firstOrNull()
      rebaseLog.invariant(maybeUnack?.transaction?.id != broadcast.transactionId) {
        "bottom of the rebase log was rejected. Please make sure that for any shared value: value == deserialize(serialize(value))"
      }
      this
    }
    RemoteKernel.Broadcast.Reset -> {
      throw RpcClientDisconnectedException("broadcast channel reset", null)
    }
  }
}

private fun stateStr(state: RebaseLoopState): String {
  return when {
    state.rebaseLog.isRebasing() -> "REBASING"
    state.rebaseLog.speculation.isEmpty() && state.rebaseLog.rebasing.isEmpty() -> "IN-SYNC"
    state.rebaseLog.rebasing.isEmpty() -> "SPECULATING"
    else -> error("")
  }
}

private fun unconfirmedInstructions(state: RebaseLoopState): List<List<SharedInstruction>> {
  return (state.rebaseLog.speculation.entries.concat(state.rebaseLog.rebasing.entries))
    .map { rebasableChange -> sharedInstructions(rebasableChange.sharedBlocks) }
}
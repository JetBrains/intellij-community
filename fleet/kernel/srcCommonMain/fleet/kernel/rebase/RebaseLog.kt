// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import com.jetbrains.rhizomedb.*
import fleet.kernel.*
import fleet.rpc.core.AssumptionsViolatedException
import fleet.util.BifurcanVector
import fleet.util.IBifurcanVector
import fleet.util.UID
import fleet.fastutil.ints.Int2ObjectOpenHashMap
import fleet.fastutil.ints.IntMap
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

internal data class LogSegment(val base: DB,
                               val entries: IBifurcanVector<RebaseLogEntry>) {
  companion object {
    fun empty(db: DB): LogSegment {
      return LogSegment(base = db,
                        entries = BifurcanVector())
    }
  }
}

internal fun LogSegment.isEmpty(): Boolean = entries.isEmpty()

internal typealias RebaseFn = (DB, RebaseLogEntry) -> RebaseLogEntry

internal fun LogSegment.advance(rebase: RebaseFn): Pair<LogSegment, RebaseLogEntry> {
  val rebasedEntry = rebase(base, entries.first())
  return LogSegment(base = rebasedEntry.dbAfter,
                    entries = entries.removeFirst()) to rebasedEntry
}

internal fun LogSegment.append(entry: RebaseLogEntry): LogSegment {
  return copy(entries = entries.addLast(entry))
}

internal data class EffectsAndNovelty(val effects: List<Effect>, val novelty: Novelty) {
  companion object {
    fun empty(): EffectsAndNovelty {
      return EffectsAndNovelty(emptyList(), Novelty.Empty)
    }
  }

  operator fun plus(other: EffectsAndNovelty): EffectsAndNovelty {
    return EffectsAndNovelty(effects + other.effects, novelty + other.novelty)
  }
}

internal data class RebaseLog(val speculation: LogSegment,
                              val rebasing: LogSegment,
                              val sendEpoch: Long) {
  init {
    val lastSpeculativeDB = when {
      speculation.isEmpty() -> speculation.base
      else -> speculation.entries.last().dbAfter
    }
    invariant(rebasing.base == lastSpeculativeDB) { "very internal invariant broken" }
  }

  companion object {
    fun empty(db: DB): RebaseLog {
      val emptySegment = LogSegment.empty(db)
      return RebaseLog(speculation = emptySegment,
                       rebasing = emptySegment,
                       sendEpoch = 1L)
    }
  }

  fun debugString(): String {
    return buildString {
      appendLine("rebaseLog dump:")
      appendLine("speculation:")
      speculation.entries.map { e -> e.transaction }.forEach { tx ->
        appendLine(tx)
      }
      appendLine("rebasing:")
      rebasing.entries.map { e -> e.transaction }.forEach { tx ->
        appendLine(tx)
      }
    }
  }
}

internal fun RebaseLog.isEmpty(): Boolean = speculation.isEmpty() && rebasing.isEmpty()

internal fun RebaseLog.advance(): Pair<RebaseLog, RebaseLogEntry> {
  return when {
    !this.speculation.isEmpty() -> {
      val (speculationPrime, change) = this.speculation.advance { _, e -> e }
      copy(speculation = speculationPrime) to change
    }
    !this.rebasing.isEmpty() -> {
      val (rebasingPrime, change) = this.rebasing.advance { db, entry ->
        checkNotNull(entry.replay(db)) {
          "rebase log entry invalidated unexpectedly: $entry"
        }
      }
      copy(rebasing = rebasingPrime,
           speculation = speculation.copy(base = rebasingPrime.base)) to change
    }
    else -> error("rebase log is empty")
  }
}

internal fun RebaseLog.firstOrNull(): RebaseLogEntry? {
  return speculation.entries.firstOrNull() ?: rebasing.entries.firstOrNull()
}

internal fun RebaseLog.reset(committedDB: DB): RebaseLog {
  return copy(speculation = LogSegment(base = committedDB,
                                       entries = BifurcanVector()),
              rebasing = LogSegment(base = committedDB,
                                    entries = this.speculation.entries.concat(rebasing.entries)))
}

internal fun RebaseLog.append(entry: RebaseLogEntry): RebaseLog {
  return copy(rebasing = rebasing.append(entry))
}

internal fun RebaseLog.consumeTx(tx: Transaction,
                                 instructionDecoder: InstructionDecoder): Pair<RebaseLog, EffectsAndNovelty> {
  try {
    val (committedDB, effectsAndNovelty) = playTransactionFromRemote(db = speculation.base,
                                                                     transaction = tx,
                                                                     instructionDecoder = instructionDecoder)
    return reset(committedDB) to effectsAndNovelty
  }
  catch (x: Throwable) {
    throw RebaseLoopInvariantBroken("failed to play transaction from remote $tx\n${debugString()}", x)
  }
}

internal class RebaseLoopInvariantBroken(message: String, reason: Throwable? = null) : Exception(message, reason)

@OptIn(ExperimentalContracts::class)
internal fun RebaseLog.invariant(b: Boolean, f: () -> String) {
  contract {
    returns() implies b
  }

  if (!b) {
    throw RebaseLoopInvariantBroken("rebase loop invariant broken " + f() + "\n${debugString()}")
  }
}

internal fun RebaseLogEntry.effects(): List<InstructionEffect> =
  sharedBlocks.flatMap { block ->
    when (block) {
      is InstructionsPair -> block.sharedEffects
      is ReconsiderableBlock -> block.items.flatMap { it.sharedEffects }
    }
  }

internal fun RebaseLog.ack(txId: UID, failed: Boolean): Triple<RebaseLog, EffectsAndNovelty, Transaction> {
  val unack = this.firstOrNull()
  invariant(unack != null) {
    "no transaction to ack: $txId"
  }
  invariant(unack.transaction != null) {
    "failed to ack $txId, first item in rebase log ($unack) has no transaction"
  }
  invariant(unack.transaction.id == txId) {
    "first item in rebase log ($unack) has different id (${unack.transaction.id}) than ack $txId"
  }
  val (advanced, rebaseLogEntry) = advance()
  invariant(rebaseLogEntry.replayFailed == failed) {
    "different success status for tx $txId, failed-locally: ${rebaseLogEntry.replayFailed} failed-remotely: $failed"
  }

  val (finalRebaseLog, localChanges) = advanced.skipLocalChanges()
  return Triple(finalRebaseLog,
                EffectsAndNovelty(
                  effects = (listOf(rebaseLogEntry) + localChanges).flatMap(RebaseLogEntry::effects),
                  novelty = (listOf(rebaseLogEntry) + localChanges).flatMap { c -> c.sharedNovelty }.toNovelty()),
                unack.transaction)
}

internal fun playTransactionFromRemote(
  db: DB,
  transaction: Transaction,
  instructionDecoder: InstructionDecoder
): Pair<DB, EffectsAndNovelty> = run {
  val effects = ArrayList<InstructionEffect>()
  val change = db.change {
    context.alter(context.impl
                    .expandingWithReadTracking()
                    .delayingEffects(effects::add)) {
      val deserContext = InstructionDecodingContext(uidAttribute = uidAttribute(),
                                                    decoder = instructionDecoder)
      for (i in transaction.instructions) {
        val instructions = with(instructionDecoder) {
          decode(deserContext, i)
        }
        instructions.forEach { mutate(it) }
      }
    }
  }
  val dbAfter = change.dbAfter.selectPartitions(setOf(SharedPart))
  dbAfter to EffectsAndNovelty(effects, change.novelty)
}

internal fun RebaseLog.skipLocalChanges(): Pair<RebaseLog, List<RebaseLogEntry>> {
  val ackChanges = mutableListOf<RebaseLogEntry>()
  var l = this
  while (!l.isEmpty() && l.firstOrNull()!!.transaction == null) {
    val (ll, c) = l.advance()
    ackChanges.add(c)
    l = ll
  }
  return Pair(l, ackChanges)
}

internal fun RebaseLog.speculativeIdMappings(): List<IntMap<UID>> {
  return speculation.entries.map { entry -> entry.idMapping }
}

internal fun RebaseLog.continueRebase(encoder: InstructionEncoder): Pair<RebaseLog, Transaction?> {
  val nearestFuture = rebasing.entries.first()

  val (rebasingPrime, rebasedEntry) = rebasing.advance { base, rebaseLogEntry ->
    rebaseLogEntry.replay(base) ?: rebaseLogEntry.reconsider(base = base,
                                                             encoder = encoder,
                                                             speculativeIdMappings = speculativeIdMappings())
  }

  val hadTransaction = nearestFuture.transaction != null
  val hasTransaction = rebasedEntry.transaction != null
  require(hasTransaction == hadTransaction) { "rebase loop invariant broken, call jetzajac" }

  val newSendEpoch = if (nearestFuture.transaction?.id != rebasedEntry.transaction?.id) sendEpoch + 1 else sendEpoch
  val rebaseLogPrime = copy(speculation = speculation.append(rebasedEntry.copy(sendEpoch = newSendEpoch)),
                            rebasing = rebasingPrime,
                            sendEpoch = newSendEpoch)
  return rebaseLogPrime to rebasedEntry.transaction.takeIf { newSendEpoch != rebasedEntry.sendEpoch }
}

internal fun RebaseLog.isRebasing(): Boolean {
  return !rebasing.isEmpty()
}

internal typealias Effect = InstructionEffect

internal sealed class SharedBlock

// we need to pass replaying function literally for stacktrace accuracy
internal data class ReconsiderableBlock(val items: List<InstructionsPair>,
                                        val eidMemoizer: Memoizer<EID>,
                                        val uidMemoizer: Memoizer<UID>,
                                        val reconsider: SharedChangeScope.() -> Any?) : SharedBlock()

internal data class InstructionsPair(val sharedInstruction: SharedInstruction?,
                                     val localInstruction: Instruction,
                                     val sharedEffects: List<InstructionEffect>,
                                     val sharedNovelty: Novelty) : SharedBlock()

internal val SharedBlock.sharedNovelty: Novelty
  get() =
    when (this) {
      is InstructionsPair -> sharedNovelty
      is ReconsiderableBlock -> items.map(InstructionsPair::sharedNovelty).fold(Novelty.Empty, Novelty::plus)
    }

internal data class RebaseLogEntry(val sharedBlocks: List<SharedBlock>,
                                   val idMapping: IntMap<UID>,
                                   val dbBefore: DB,
                                   val dbAfter: DB,
                                   val transaction: Transaction?,
                                   val sendEpoch: Long,
                                   val replayFailed: Boolean)

internal val RebaseLogEntry.sharedNovelty: Novelty
  get() = sharedBlocks.map(SharedBlock::sharedNovelty).fold(Novelty.Empty, Novelty::plus)


internal fun sharedInstructions(sharedBlocks: List<SharedBlock>): List<SharedInstruction> =
  sharedBlocks.flatMap { block ->
    when (block) {
      is InstructionsPair -> listOfNotNull(block.sharedInstruction)
      is ReconsiderableBlock -> block.items.mapNotNull { i -> i.sharedInstruction }
    }
  }

internal fun DbContext<Mut>.playInstructionPair(instructionPair: InstructionsPair): InstructionsPair = run {
  val effects = ArrayList<InstructionEffect>()
  val novelty = alter(impl.expandingWithReadTracking().delayingEffects(effects::add)) {
    mutate(instructionPair.localInstruction)
  }
  val sharedNovelty = novelty.filter { partition(it.eid) == SharedPart }.toNovelty()
  InstructionsPair(sharedInstruction = instructionPair.sharedInstruction,
                   localInstruction = instructionPair.localInstruction,
                   sharedEffects = effects,
                   sharedNovelty = sharedNovelty)
}


internal fun RebaseLogEntry.replay(base: DB): RebaseLogEntry? = let { logEntry ->
  when {
    base == dbBefore -> logEntry
    else ->
      try {
        val mutableIdMapping = Int2ObjectOpenHashMap<UID>()
        val (sharedBlocksPrime, change) = base.changeAndReturn {
          context.alter(context.impl.collectingNovelty { datom ->
            if (datom.attr == uidAttribute()) {
              mutableIdMapping[datom.eid] = datom.value as UID
            }
          }) {
            logEntry.sharedBlocks.map { block ->
              when (block) {
                is InstructionsPair -> playInstructionPair(block)
                is ReconsiderableBlock -> block.copy(
                  items = block.items.map { item -> playInstructionPair(item) }
                )
              }
            }
          }
        }
        logEntry.copy(dbBefore = base,
                      sharedBlocks = sharedBlocksPrime,
                      dbAfter = change.dbAfter.selectPartitions(setOf(SharedPart)),
                      idMapping = mutableIdMapping,
                      replayFailed = false)
      }
      catch (x: AssumptionsViolatedException) {
        null
      }
      catch (x: Throwable) {
        RebaseLogger.logger.error(x) {
          "failed to replay: $sharedBlocks"
        }
        logEntry.copy(dbAfter = base,
                      dbBefore = base,
                      replayFailed = true)
      }
  }
}

internal fun RebaseLogEntry.reconsider(base: DB,
                                       encoder: InstructionEncoder,
                                       speculativeIdMappings: List<IntMap<UID>>): RebaseLogEntry {
  return try {
    val mutableIdMapping = Int2ObjectOpenHashMap<UID>()
    val uidAttribute = uidAttribute()
    fun updateIdMapping(datom: Datom) {
      if (datom.attr == uidAttribute) {
        mutableIdMapping.put(datom.eid, datom.value as UID)
      }
    }

    val idMappings = speculativeIdMappings + mutableIdMapping


    val (newSharedBlocks, change) = base.changeAndReturn {
      sharedBlocks.map { block ->
        when (block) {
          is InstructionsPair ->
            context.alter(context.impl.collectingNovelty(::updateIdMapping)) {
              playInstructionPair(block)
            }
          is ReconsiderableBlock ->
            block.copy(
              items =
                sharedImpl(
                  eidMemoizer = block.eidMemoizer,
                  uidMemoizer = block.uidMemoizer,
                  mutableNovelty = ::updateIdMapping,
                  f = block.reconsider,
                  instructionEncoder = encoder,
                  idMappings = idMappings
                ).second
            )
        }
      }
    }

    reconsidered(dbAfter = change.dbAfter.selectPartitions(setOf(SharedPart)),
                 dbBefore = base,
                 idMapping = mutableIdMapping,
                 newSharedBlocks = newSharedBlocks)
  }
  catch (e: Throwable) {
    RebaseLogger.logger.error(e) {
      "failed to reconsider shared transaction. rebaseLogEntry: \n${this}"
    }

    reconsidered(dbAfter = base,
                 dbBefore = base,
                 idMapping = Int2ObjectOpenHashMap(),
                 newSharedBlocks = emptyList())
  }
}

internal fun RebaseLogEntry.reconsidered(newSharedBlocks: List<SharedBlock>,
                                         dbBefore: DB,
                                         dbAfter: DB,
                                         idMapping: IntMap<UID>): RebaseLogEntry {
  requireNotNull(transaction) // what are we reconsidering otherwise?
  return copy(sharedBlocks = newSharedBlocks,
              idMapping = idMapping,
              dbBefore = dbBefore,
              dbAfter = dbAfter,
              replayFailed = false,
              transaction = Transaction(instructions = sharedInstructions(newSharedBlocks),
                                        origin = transaction.origin,
                                        index = transaction.index,
                                        id = UID.random()))
}

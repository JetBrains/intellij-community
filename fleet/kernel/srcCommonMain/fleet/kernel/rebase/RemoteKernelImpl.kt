// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import com.jetbrains.rhizomedb.*
import fleet.kernel.*
import fleet.rpc.core.AssumptionsViolatedException
import fleet.rpc.core.toRpc
import fleet.util.UID
import fleet.util.async.takeUntilInclusive
import fleet.util.logging.KLoggers
import fleet.fastutil.ints.IntList
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.flow.*

object TransactionResultKey : ChangeScopeKey<TransactionResult>

sealed class TransactionResult {
  data class TransactionApplied(val tx: Transaction) : TransactionResult()
  data class TransactionFailed(val tx: Transaction, val x: Throwable) : TransactionResult()
  data class TransactionRejected(val tx: Transaction) : TransactionResult()
}

fun <T> CoroutineScope.waitForCompletion(flow: Flow<T>): Flow<T> {
  val job = Job(coroutineContext.job)
  return flow.onCompletion {
    job.complete()
  }
}

class RemoteKernelImpl(
  val transactor: Transactor,
  val coroutineScope: CoroutineScope,
  val instructionDecoder: InstructionDecoder,
) : RemoteKernel {

  private companion object {
    val log = KLoggers.logger(RemoteKernelImpl::class)
  }

  override suspend fun subscribe(author: UID?): RemoteKernel.Subscription {
    val result = CompletableDeferred<RemoteKernel.Subscription>()
    coroutineScope.launch {
      val events = transactor.log.produceIn(this)
      val first = events.receive()
      require(first is SubscriptionEvent.First)
      val snapshot = asOf(first.db) {
        val sharedDatoms = queryIndex(IndexQuery.All(IntList.of(SharedPart)))
        val problematicDatoms = sharedDatoms.filter {
          getOne(it.eid, uidAttribute()) == null
        }.toList()
        require(problematicDatoms.isEmpty()) {
          "no uids in shared datoms: ${problematicDatoms.joinToString { displayDatom(it) }}"
        }
        buildDurableSnapshot(sharedDatoms, emptySet())
      }
      val vectorClock = asOf(first.db) { WorkspaceClockEntity.clientClock.vectorClock.clock }
      val broadcastFlow = events.consumeAsFlow()
        .mapNotNull { event ->
          when (event) {
            is SubscriptionEvent.First -> error("first should have been consumed already")
            is SubscriptionEvent.Next -> {
              event.change.meta[TransactionResultKey]?.let { transactionResult ->
                when (transactionResult) {
                  is TransactionResult.TransactionApplied ->
                    when {
                      transactionResult.tx.origin == author ->
                        RemoteKernel.Broadcast.Ack(transactionResult.tx.id)

                      else ->
                        RemoteKernel.Broadcast.Tx(transactionResult.tx.let { tx ->
                          tx.copy(instructions = tx.instructions.filter { instruction -> instruction.name != ValidateCoder.instructionName })
                        })
                    }

                  is TransactionResult.TransactionFailed ->
                    RemoteKernel.Broadcast.Failure(origin = transactionResult.tx.origin,
                                                   transactionId = transactionResult.tx.id)

                  is TransactionResult.TransactionRejected ->
                    when {
                      transactionResult.tx.origin == author ->
                        RemoteKernel.Broadcast.Rejection(transactionResult.tx.id)

                      else -> null
                    }
                }
              }
            }
            is SubscriptionEvent.Reset -> RemoteKernel.Broadcast.Reset
          }
        }
        .takeUntilInclusive { it is RemoteKernel.Broadcast.Reset }

      result.complete(RemoteKernel.Subscription(
        snapshot = snapshot.entities.asFlow().toRpc(),
        txs = waitForCompletion(broadcastFlow).toRpc(),
        vectorClock = vectorClock
      ))
    }.invokeOnCompletion {
      result.completeExceptionally(it ?: RuntimeException("saga forgot to complete deferred"))
    }
    return result.await()
  }

  override suspend fun transact(frontendTxs: ReceiveChannel<Transaction>) {
    coroutineScope.launch(start = CoroutineStart.ATOMIC) {
      frontendTxs.consume {
        while (true) {
          runCatching<Transaction> { frontendTxs.receive() }
            .onFailure {
              /*
              * client failures should not affect workspace kernel, and it is nowhere to propagate to
              * */
              return@launch
            }
            .onSuccess { transaction ->
              transactor.changeSuspend {
                context.run {
                  val index = WorkspaceClockEntity.clientClock.vectorClock.clock[transaction.origin] ?: 0L
                  if (index + 1 == transaction.index) {
                    try {
                      val uidAttribute = uidAttribute()
                      val deserContext = InstructionDecodingContext(uidAttribute = uidAttribute,
                                                                    decoder = instructionDecoder)
                      val mutableNovelty = meta[MutableNoveltyKey]!!
                      val effects = ArrayList<InstructionEffect>()
                      alter(impl.mutableDb
                              .collectingNovelty(mutableNovelty::add)
                              .expandingWithReadTracking()
                              .delayingEffects(effects::add)) {
                        for (i in transaction.instructions) {
                          val instructions = instructionDecoder.run {
                            decode(deserContext, i)
                          }
                          instructions.forEach {
                            mutate(it)
                          }
                        }
                      }
                      // todo: we could run effects in a same change,
                      // todo: but that would result in more than one TransactionResult per Change
                      // todo: and this is something, CodeInsightGateway does not expect.
                      if (effects.isNotEmpty()) {
                        transactor.changeAsync {
                          effects.forEach { it.effect(context) }
                        }
                      }
                      WorkspaceClockEntity.tick(transaction.origin)
                      meta[TransactionResultKey] = TransactionResult.TransactionApplied(transaction)
                      log.trace { "successfully applied tx: $transaction" }
                    }
                    catch (x: AssumptionsViolatedException) {
                      impl.mutableDb.rollback(dbBefore)
                      meta[MutableNoveltyKey] = MutableNovelty()
                      log.debug { "Rejecting tx: $transaction because $x" }
                      meta[TransactionResultKey] = TransactionResult.TransactionRejected(transaction)
                    }
                    catch (x: Throwable) {
                      // rollback:
                      impl.mutableDb.rollback(dbBefore)
                      meta[MutableNoveltyKey] = MutableNovelty()
                      log.error(x) { "transaction failed: $transaction" }

                      WorkspaceClockEntity.tick(transaction.origin)
                      meta[TransactionResultKey] = TransactionResult.TransactionFailed(transaction, x)
                    }
                  }
                  else {
                    log.debug { "Rejecting tx: $transaction because tx.index = ${transaction.index}, last index = $index" }
                  }
                }
              }
            }
        }
      }
    }
  }
}
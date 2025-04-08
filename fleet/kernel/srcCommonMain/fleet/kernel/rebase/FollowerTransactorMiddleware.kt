// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.ChangeScope
import com.jetbrains.rhizomedb.get
import fleet.kernel.*
import fleet.util.UID
import fleet.fastutil.ints.Int2ObjectOpenHashMap
import fleet.fastutil.ints.MutableIntMap
import fleet.util.logging.logger

class FollowerTransactorMiddleware(
  private val instructionEncoder: InstructionEncoder,
) : TransactorMiddleware {

  companion object {
    private val logger = logger<FollowerTransactorMiddleware>()
  }

  override fun ChangeScope.performChange(next: ChangeScope.() -> Unit): Unit = run {
    val idMapping: MutableIntMap<UID> = Int2ObjectOpenHashMap()
    val sharedBlocks: ArrayList<SharedBlock> = ArrayList()
    val sharedDbBefore = dbBefore.selectPartitions(setOf(SharedPart))
    val uidAttribute = uidAttribute()
    val mutableNovelty = meta[MutableNoveltyKey]!!

    meta[Shared] = object : Shared {
      override fun <T> shared(f: SharedChangeScope.() -> T): T = run {
        val eidMemo = Memoizer<EID>()
        val uidMemo = Memoizer<UID>()
        val idMappings = (RemoteKernelConnectionEntity.current?.speculationData?.idMappings ?: emptyList()) + idMapping
        val (t, items) = sharedImpl(
          eidMemoizer = eidMemo,
          uidMemoizer = uidMemo,
          mutableNovelty = { datom ->
            mutableNovelty.add(datom)
            if (partition(datom.eid) == SharedPart) {
              if (datom.attr == uidAttribute) {
                idMapping.put(datom.eid, datom.value as UID)
              }
            }
          },
          f = f,
          idMappings = idMappings,
          instructionEncoder = instructionEncoder,
        )

        sharedBlocks.add(
          ReconsiderableBlock(
            reconsider = f,
            items = items,
            eidMemoizer = eidMemo,
            uidMemoizer = uidMemo
          )
        )

        t
      }
    }

    context.alter(
      context.impl
        .instructionsRecording(
          serContext = InstructionEncodingContext(
            uidAttribute = uidAttribute,
            encoder = instructionEncoder
          ),
          recorder = sharedBlocks::add
        )
        .enforcingUniquenessConstraints(AllParts)
        .preventRefsFromShared()
        .executingEffects(context)
    ) {
      next()
    }

    RemoteKernelConnectionEntity.current?.let { connection ->
      sharedBlocks
        .takeIf { it.isNotEmpty() }
        ?.let {
          RebaseLogEntry(
            sharedBlocks = sharedBlocks,
            idMapping = idMapping,
            dbBefore = sharedDbBefore,
            dbAfter = context.impl.mutableDb.snapshot().selectPartitions(setOf(SharedPart)),
            sendEpoch = 0L,
            replayFailed = false,
            transaction = sharedInstructions(sharedBlocks)
              .takeIf { it.isNotEmpty() }
              ?.let { sharedInstructions ->
                val newHand = connection.clientClock.tick()
                logger.trace { "Change ticks ${newHand.clientId} -> ${newHand.index()}" }
                connection[RemoteKernelConnectionEntity.ClientClockAttr] = newHand
                Transaction(
                  instructions = sharedInstructions,
                  origin = newHand.clientId,
                  index = newHand.index(),
                  id = UID.random()
                )
              },
          )
        }
        ?.let { rebaseLogEntry ->
          val speculationData = connection.speculationData
          connection[RemoteKernelConnectionEntity.SharedPartitionTimestampAttr]++
          connection[RemoteKernelConnectionEntity.SpeculationDataAttr] = speculationData.copy(
            novelty = speculationData.novelty + rebaseLogEntry.sharedNovelty,
            idMappings = speculationData.idMappings + rebaseLogEntry.idMapping
          )
          meta[RebaseLogEntryKey] = rebaseLogEntry
        }
    }
  }
}

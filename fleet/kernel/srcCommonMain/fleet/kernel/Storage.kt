// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.impl.EidGen
import fleet.reporting.shared.tracing.span
import fleet.reporting.shared.tracing.spannedScope
import fleet.util.UID
import fleet.util.async.catching
import fleet.util.async.use
import fleet.util.logging.KLogger
import fleet.util.logging.logger
import fleet.fastutil.ints.Int2ObjectOpenHashMap
import fleet.fastutil.ints.IntOpenHashSet
import fleet.util.computeIfAbsentShim
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

private object Storage {
  val logger = logger<Storage>()
}

const val DbSnapshotVersion: String = "11"

@OptIn(FlowPreview::class)
suspend fun <T> withStorage(
  storageKey: StorageKey,
  autoSaveDebounceMs: Long,
  loadSnapshot: suspend CoroutineScope.() -> DurableSnapshotWithPartitions, // reads snapshot from file
  saveSnapshot: suspend CoroutineScope.(DurableSnapshotWithPartitions) -> Unit, // writes snapshot to file
  serializationRestrictions: Set<KClass<*>> = emptySet(),
  body: suspend CoroutineScope.() -> T,
): T =
  coroutineScope {
    catching {
      Storage.logger.info { "loading snapshot $storageKey" }
      val snapshot = spannedScope("loadSnapshot") { loadSnapshot() }
      spannedScope("transact snapshot") {
        if (snapshot != DurableSnapshotWithPartitions.Empty) {
          Storage.logger.info { "applying non-empty snapshot $storageKey" }
          val isFailFast = currentCoroutineContext().shouldFailFast
          change {
            span("apply snapshot") {
              DbContext.threadBound.ensureMutable {
                applyDurableSnapshotWithPartitions(snapshotWithPartitions = snapshot, isFailFast = isFailFast)
              }
            }
          }
        }
      }
    }.onFailure { x ->
      // Throwing exception only if we're in test mode
      if (coroutineContext.shouldFailFast)
        throw x
      else
        Storage.logger.error(x) { "couldn't restore state for $storageKey" }
    }

    launch {
      val storedEntitiesCache = IntOpenHashSet()
      transactor().log
        .mapNotNull { event ->
          when (event) {
            is SubscriptionEvent.First, is SubscriptionEvent.Reset -> {
              asOf(event.db) {
                @Suppress("UNCHECKED_CAST")
                queryIndex(IndexQuery.LookupMany(Durable.StorageKeyAttr.attr as Attribute<StorageKey>, storageKey))
                  .forEach { storedEntitiesCache.add(it.eid) }
              }
              event.db
            }
            is SubscriptionEvent.Next ->
              event.change.dbAfter.takeIf {
                event.change.novelty.fold(false) { needToSave, datom ->
                  when {
                    // new entities are being added/removed to the store
                    datom.value == storageKey && datom.attr == Durable.StorageKeyAttr.attr -> {
                      val eid = datom.eid
                      when (datom.added) {
                        true -> storedEntitiesCache.add(eid)
                        false -> storedEntitiesCache.remove(eid)
                      }
                      true
                    }
                    // stored entities are being changed
                    datom.eid in storedEntitiesCache -> true
                    else -> needToSave
                  }
                }
              }
          }
        }
        .debounce(autoSaveDebounceMs)
        .collectLatest { db ->
          Storage.logger.debug { "saving snapshot $storageKey" }
          val (snapshot, snapshotBuildDuration) = measureTimedValue {
            asOf(db) {
              durableSnapshotWithPartitions(storageKey, serializationRestrictions)
            }
          }
          val entitiesCount = snapshot.snapshot.entities.size
          Storage.logger.debug { "snapshot for $storageKey built with $entitiesCount entities, took $snapshotBuildDuration" }
          val savingDuration = measureTime {
            saveSnapshot(snapshot)
          }
          Storage.logger.debug { "successfully saved snapshot for $storageKey, written in $savingDuration" }
        }
    }.use {
      body()
    }.also {
      Storage.logger.info { "last save for $storageKey " }
      saveSnapshot(asOf(transactor().lastKnownDb) {
        durableSnapshotWithPartitions(storageKey, serializationRestrictions)
      })
    }
  }

@Suppress("UNCHECKED_CAST")
private fun storageKeyAttr(): Attribute<StorageKey> {
  return Durable.StorageKeyAttr.attr as Attribute<StorageKey>
}

@Serializable
data class DurableSnapshotWithPartitions(
  val snapshot: DurableSnapshot,
  val partitions: Map<UID, Int>,
) {
  companion object {
    val Empty: DurableSnapshotWithPartitions = DurableSnapshotWithPartitions(DurableSnapshot.Empty, emptyMap())
  }
}

private fun DbContext<Mut>.applyDurableSnapshotWithPartitions(snapshotWithPartitions: DurableSnapshotWithPartitions, isFailFast: Boolean) {
  span("applyDurableSnapshotWithPartitions") {
    val memoizedEIDs = HashMap<UID, EID>()
    applySnapshotNew(snapshotWithPartitions.snapshot) { uid ->
      val partition = snapshotWithPartitions.partitions[uid]!!
      memoizedEIDs.computeIfAbsentShim(uid) { EidGen.freshEID(partition) }
    }

    val attrIdents = snapshotWithPartitions.snapshot.entities.flatMapTo(HashSet()) { e -> e.attrs.keys }
    val deserializationProblems = deserializationProblems(attrIdents.mapNotNull { k -> attributeByIdent(k.ident) })
    if (isFailFast) {
      check(deserializationProblems.isEmpty()) { deserializationProblems.joinToString(separator = "\n") }
    }

    val schemaProblems = uidAttribute().let { uidAttr ->
      snapshotWithPartitions.snapshot.entities.flatMap { durableEntity ->
        lookupOne(uidAttr, durableEntity.uid)?.let { entityEID ->
          entityType(entityEID)?.let { entityTypeEID ->
            missingRequiredAttrs(entityEID, entityTypeEID)
          }
        } ?: emptyList()
      }
    }

    reportDeserializationProblems(deserializationProblems, Storage.logger)
    reportSchemaProblems(schemaProblems, Storage.logger)
    if (isFailFast) {
      check(schemaProblems.isEmpty()) { schemaProblems.joinToString(separator = "\n") }
    }

    val entitiesToRetract = (deserializationProblems.map { problem -> problem.datom.eid }
                             + schemaProblems.map(MissingRequiredAttribute::eid))
    entitiesToRetract.forEach { eid ->
      retractEntity(eid)
    }
  }
}

private fun durableSnapshotWithPartitions(
  storageKey: StorageKey,
  serializationRestrictions: Set<KClass<*>>,
): DurableSnapshotWithPartitions {
  return with(DbContext.threadBound) {
    val storageKeyAttr = storageKeyAttr()
    val uidAttribute = uidAttribute()

    val skippedEids = IntOpenHashSet()
    val datomsToStore = Int2ObjectOpenHashMap<List<Datom>>()
    val visitedEids = IntOpenHashSet()
    fun dfs(eid: EID): Boolean =
      when {
        skippedEids.contains(eid) -> false
        datomsToStore.containsKey(eid) -> true
        !visitedEids.add(eid) -> true
        queryIndex(IndexQuery.Contains(eid, storageKeyAttr, storageKey)) == null -> {
          skippedEids.add(eid)
          false
        }
        else -> {
          val entityDatoms = queryIndex(IndexQuery.Entity(eid))
          val refDatoms = entityDatoms.filter { it.attr.schema.isRef && it.attr != Entity.Type.attr }
          val shouldBeSaved = refDatoms.fold(true) { acc, refDatom ->
            acc && (dfs(refDatom.value as EID) || !refDatom.attr.schema.required)
          }
          if (shouldBeSaved) {
            datomsToStore[eid] = entityDatoms.filterNot { datom ->
              datom.attr.schema.isRef && skippedEids.contains(datom.value as EID)
            }
          }
          else {
            Storage.logger.warn {
              "Entity ${entity(eid)} is skipped from durable serialization " +
              "because it have required property that is not to be saved with the same storageKey"
            }
            skippedEids.add(eid)
          }
          shouldBeSaved
        }
      }

    queryIndex(IndexQuery.LookupMany(storageKeyAttr, storageKey)).forEach { datom -> dfs(datom.eid) }
    val datoms = datomsToStore.values.asSequence().toList().flatten()
    val snapshot = buildDurableSnapshot(datoms.asSequence(), serializationRestrictions)
    DurableSnapshotWithPartitions(snapshot = snapshot,
                                  partitions = datoms.mapNotNull { (e, a, v) ->
                                    when (a) {
                                      uidAttribute -> v as UID to partition(e)
                                      else -> null
                                    }
                                  }.toMap())
  }
}

fun DbContext<Q>.reportDeserializationProblems(problems: List<DeserializationProblem>, kLogger: KLogger) {
  problems.forEach { problem ->
    kLogger.error((problem as? DeserializationProblem.Exception)?.throwable) {
      when (problem) {
        is DeserializationProblem.Exception -> "Got serialization exception"
        is DeserializationProblem.GotNull -> "Got null"
        is DeserializationProblem.Unexpected -> "Not a json element"
      } + ", datom: ${displayDatom(problem.datom)}"
    }
  }
}

fun DbContext<Q>.reportSchemaProblems(problems: List<MissingRequiredAttribute>, kLogger: KLogger) {
  if (problems.isNotEmpty()) {
    kLogger.error {
      problems.joinToString(separator = "\n",
                            prefix = "Retracting entities missing required attributes") { p -> message(p) }
    }
  }
}
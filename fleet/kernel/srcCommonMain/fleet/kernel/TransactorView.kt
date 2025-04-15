// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.impl.Editor
import fleet.kernel.rebase.runOfferContributors
import fleet.reporting.shared.tracing.spannedScope
import fleet.util.async.view
import fleet.util.openmap.MutableOpenMap
import fleet.fastutil.ints.IntArrayList
import fleet.fastutil.ints.IntList
import fleet.fastutil.ints.contains
import fleet.fastutil.ints.retainAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer

private data class TransactorViewEntity2(override val eid: EID) : Entity {

  companion object : EntityType<TransactorViewEntity2>(TransactorViewEntity2::class, ::TransactorViewEntity2) {
    val DefaultPart = requiredValue("defaultPart", Int.serializer(), Indexing.UNIQUE)
    val HiddenPart = requiredValue("hiddenPart", Int.serializer(), Indexing.UNIQUE)
    val QueryCache = requiredTransient<QueryCache>("queryCache")

    fun forDefaultPart(defaultPart: Part): TransactorViewEntity2? =
      entity(DefaultPart, defaultPart)
  }

  fun counterPart(): TransactorViewEntity2? =
    entity(DefaultPart, this[HiddenPart])
}


private fun DB.subDB(hiddenPart: Part, kernelViewEntity: TransactorViewEntity2): DB {
  //TODO: some perf here?
  val queryCache = asOf(this) {
    kernelViewEntity[TransactorViewEntity2.QueryCache]
  }
  return DB(index = index.setPartition(Editor(), hiddenPart, null),
            queryCache = queryCache)
}

private fun Change.subChange(hiddenPart: Part, kernelViewEntity: TransactorViewEntity2): Change =
  Change(dbBefore = dbBefore.subDB(hiddenPart, kernelViewEntity),
         dbAfter = dbAfter.subDB(hiddenPart, kernelViewEntity),
         novelty = novelty.filter { d -> partition(d.eid) != hiddenPart }.toNovelty(),
         meta = meta)

private fun requireInPartition(eid: EID, parts: IntList): Nothing? =
  if (parts.contains(partition(eid))) null
  else error("attempted to query hidden partition eid: $eid partition: ${partition(eid)}, allowed parts: $parts")

fun <T> IndexQuery<T>.withOverridenPartitions(parts: IntList): IndexQuery<T> = let { indexQuery ->
  when (indexQuery) {
    is IndexQuery.All -> indexQuery.copy(partitions = parts)
    is IndexQuery.Column<*> -> indexQuery.copy(partitions = parts)
    is IndexQuery.LookupMany<*> -> indexQuery.copy(partitions = parts)
    is IndexQuery.LookupUnique<*> -> indexQuery.copy(partitions = parts)
    is IndexQuery.RefsTo -> indexQuery.copy(partitions = parts)
    is IndexQuery.Contains<*> -> {
      requireInPartition(indexQuery.eid, parts)
      indexQuery
    }
    is IndexQuery.Entity -> {
      requireInPartition(indexQuery.eid, parts)
      indexQuery
    }
    is IndexQuery.GetMany<*> -> {
      requireInPartition(indexQuery.eid, parts)
      indexQuery
    }
    is IndexQuery.GetOne<*> -> {
      requireInPartition(indexQuery.eid, parts)
      indexQuery
    }
  } as IndexQuery<T>
}

fun <T> IndexQuery<T>.withIntersectedPartitions(parts: IntList): IndexQuery<T> = let { indexQuery ->
  when (indexQuery) {
    is IndexQuery.All -> indexQuery.copy(partitions = parts.intersect(indexQuery.partitions))
    is IndexQuery.Column<*> -> indexQuery.copy(partitions = parts.intersect(indexQuery.partitions))
    is IndexQuery.LookupMany<*> -> indexQuery.copy(partitions = parts.intersect(indexQuery.partitions))
    is IndexQuery.LookupUnique<*> -> indexQuery.copy(partitions = parts.intersect(indexQuery.partitions))
    is IndexQuery.RefsTo -> indexQuery.copy(partitions = parts.intersect(indexQuery.partitions))
    is IndexQuery.Contains<*> -> {
      requireInPartition(indexQuery.eid, parts)
      indexQuery
    }
    is IndexQuery.Entity -> {
      requireInPartition(indexQuery.eid, parts)
      indexQuery
    }
    is IndexQuery.GetMany<*> -> {
      requireInPartition(indexQuery.eid, parts)
      indexQuery
    }
    is IndexQuery.GetOne<*> -> {
      requireInPartition(indexQuery.eid, parts)
      indexQuery
    }
  } as IndexQuery<T>
}

fun Mut.overridingQueryPartitions(parts: IntList): Mut = let { mut ->
  object : Mut by mut {
    override fun <T> queryIndex(indexQuery: IndexQuery<T>): T =
      mut.queryIndex(indexQuery.withOverridenPartitions(parts))
  }
}

fun Mut.intersectingPartitions(parts: IntList): Mut = let { mut ->
  object : Mut by mut {
    override fun <T> queryIndex(indexQuery: IndexQuery<T>): T =
      mut.queryIndex(indexQuery.withIntersectedPartitions(parts))
  }
}

fun Q.overridingQueryPartitions(parts: IntList): Q = let { mut ->
  object : Q by mut {
    override fun <T> queryIndex(indexQuery: IndexQuery<T>): T =
      mut.queryIndex(indexQuery.withOverridenPartitions(parts))
  }
}

fun IntList.intersect(other: IntList): IntList {
  val result = IntArrayList(this)
  result.retainAll(other)
  return result
}

fun Q.intersectingPartitions(parts: IntList): Q = let { mut ->
  object : Q by mut {
    override fun <T> queryIndex(indexQuery: IndexQuery<T>): T =
      mut.queryIndex(indexQuery.withIntersectedPartitions(parts))
  }
}

fun Mut.cachedQueryWithParts(parts: IntList): Mut = let { mut ->
  object : Mut by mut {
    override fun <T> DbContext<Q>.cachedQuery(query: CachedQuery<T>): CachedQueryResult<T> =
      alter(impl.overridingQueryPartitions(parts)) { mut.run { cachedQuery(query) } }
  }
}

fun Mut.expandAndMutateWithParts(parts: IntList): Mut = let { mut ->
  object : Mut by mut {
    override fun expand(pipeline: DbContext<Q>, instruction: Instruction): Expansion =
      pipeline.alter(pipeline.impl.overridingQueryPartitions(parts)) {
        mut.expand(this, instruction)
      }

    override fun mutate(pipeline: DbContext<Mut>, expansion: Expansion): Novelty =
      pipeline.alter(pipeline.impl.overridingQueryPartitions(parts)) {
        mut.mutate(this, expansion)
      }
  }
}

private fun IntList.except(element: Int): IntList =
  IntArrayList(this).apply { removeAt(element) }

private fun <T> ChangeScope.withTransactorView(kernelViewEntity: TransactorViewEntity2, body: ChangeScope.() -> T): T =
  let { changeScope ->
    val hiddenPart = kernelViewEntity[TransactorViewEntity2.HiddenPart]
    val visiblePart = kernelViewEntity[TransactorViewEntity2.DefaultPart]
    val queryCache = kernelViewEntity[TransactorViewEntity2.QueryCache]
    val mut = context.impl.mutableDb
    val oldQueryCache = mut.queryCache
    val partsExceptHidden = AllParts.except(hiddenPart)
    val partsExceptVisible = AllParts.except(visiblePart)
    mut.queryCache = queryCache
    val i = context.impl
    var hiddenPartContext: Mut? = null
    var visiblePartContext: Mut? = null
    var sharedPartContext: Mut? = null

    fun effectsContext(effect: InstructionEffect): Mut = run {
      val instruction = effect.origin
      when {
        instruction is RetractEntityInPartition -> {
          when (val part = partition(instruction.eid)) {
            hiddenPart -> hiddenPartContext!!
            visiblePart -> visiblePartContext!!
            SharedPart -> sharedPartContext!!
            else -> error("effects in partition $part are not supported")
          }
        }
        else -> visiblePartContext!!
      }
    }

    sharedPartContext = i
      .intersectingPartitions(IntList.of(SchemaPart, CommonPart, SharedPart))
      .cachedQueryWithParts(IntList.of(SchemaPart, CommonPart, SharedPart))
      .expandAndMutateWithParts(AllParts)
      .enforcingUniquenessConstraints(IntList.of(SchemaPart, CommonPart, SharedPart))
      .withDefaultPart(SharedPart)
      .executingEffects(::effectsContext)

    hiddenPartContext = i
      .intersectingPartitions(partsExceptVisible)
      .cachedQueryWithParts(partsExceptVisible)
      .expandAndMutateWithParts(AllParts)
      .enforcingUniquenessConstraints(partsExceptVisible)
      .withDefaultPart(hiddenPart)
      .executingEffects(::effectsContext)

    visiblePartContext = i
      .intersectingPartitions(partsExceptHidden)
      .cachedQueryWithParts(partsExceptHidden)
      .expandAndMutateWithParts(AllParts)
      .enforcingUniquenessConstraints(partsExceptHidden)
      .withDefaultPart(visiblePart)
      .executingEffects(::effectsContext)

    val res = context.alter(visiblePartContext) {
      body()
    }

    val newQueryCache = mut.queryCache
    val novelty = meta[MutableNoveltyKey]!!

    mut.queryCache = oldQueryCache.invalidate(novelty)
    kernelViewEntity[TransactorViewEntity2.QueryCache] = newQueryCache

    kernelViewEntity.counterPart()?.let { counterpart ->
      counterpart[TransactorViewEntity2.QueryCache] = counterpart[TransactorViewEntity2.QueryCache].invalidate(novelty)
    }
    res
  }

private fun kernelViewMiddleware(kernelViewEntity: TransactorViewEntity2): TransactorMiddleware =
  object : TransactorMiddleware {
    override fun ChangeScope.performChange(next: ChangeScope.() -> Unit) {
      DbContext.threadBound.ensureMutable {
        withTransactorView(kernelViewEntity) {
          next()
        }

        if (kernelViewEntity[TransactorViewEntity2.HiddenPart] == FrontendPart) {
          val novelty = meta[MutableNoveltyKey]!!
          val sharedNovelty = novelty.filter { datom -> partition(datom.eid) == SharedPart }.toNovelty()
          if (sharedNovelty.isNotEmpty()) {
            TransactorViewEntity2.forDefaultPart(FrontendPart)?.let { frontendKernelViewEnitty ->
              withTransactorView(frontendKernelViewEnitty) {
                runOfferContributors(sharedNovelty)
              }
            }
          }
        }
      }
    }
  }

suspend fun <T> withTransactorView(
  hiddenPart: Part,
  defaultPart: Part,
  middleware: TransactorMiddleware = TransactorMiddleware.Identity,
  body: suspend CoroutineScope.(Transactor) -> T,
): T =
  spannedScope("withKernelView $defaultPart") {
    val kernelViewEntity = change {
      register(TransactorViewEntity2)
      TransactorViewEntity2.new {
        it[TransactorViewEntity2.DefaultPart] = defaultPart
        it[TransactorViewEntity2.HiddenPart] = hiddenPart
        it[TransactorViewEntity2.QueryCache] = QueryCache.empty()
      }
    }

    val kernel = transactor()

    val myMiddleware = kernelViewMiddleware(kernelViewEntity) + middleware
    fun wrapChange(f: ChangeScope.() -> Unit): ChangeScope.() -> Unit {
      return {
        myMiddleware.run { performChange(f) }
      }
    }

    val transactorView = object : Transactor {
      override val middleware: TransactorMiddleware = kernel.middleware + myMiddleware

      override val dbState: StateFlow<DB> =
        kernel.dbState.view { db -> db.subDB(hiddenPart, kernelViewEntity) }

      override fun changeAsync(f: ChangeScope.() -> Unit): Deferred<Change> =
        kernel.changeAsync(wrapChange(f))

      override suspend fun changeSuspend(f: ChangeScope.() -> Unit): Change =
        kernel.changeSuspend(wrapChange(f)).subChange(hiddenPart, kernelViewEntity)

      override val log: Flow<SubscriptionEvent> =
        kernel.log.map { e ->
          when (e) {
            is SubscriptionEvent.First ->
              SubscriptionEvent.First(e.db.subDB(hiddenPart, kernelViewEntity))
            is SubscriptionEvent.Next ->
              SubscriptionEvent.Next(e.change.subChange(hiddenPart, kernelViewEntity))
            is SubscriptionEvent.Reset ->
              SubscriptionEvent.Reset(e.db.subDB(hiddenPart, kernelViewEntity))
          }
        }

      override val meta: MutableOpenMap<Transactor> = MutableOpenMap.empty()
    }
    withContext(transactorView + DbSource.ContextElement(FlowDbSource(transactorView.dbState, debugName = "kernelView $transactorView"))) { body(transactorView) }
  }


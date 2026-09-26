package fleet.kernel.rebase.test

import com.jetbrains.rhizomedb.ChangeScope
import com.jetbrains.rhizomedb.DbContext
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.EntityType
import com.jetbrains.rhizomedb.IndexQuery
import com.jetbrains.rhizomedb.Indexing
import com.jetbrains.rhizomedb.InstructionExpansion
import com.jetbrains.rhizomedb.Op
import com.jetbrains.rhizomedb.Q
import com.jetbrains.rhizomedb.RefFlags
import com.jetbrains.rhizomedb.RetractableEntity
import com.jetbrains.rhizomedb.all
import com.jetbrains.rhizomedb.asOf
import com.jetbrains.rhizomedb.entities
import com.jetbrains.rhizomedb.entity
import com.jetbrains.rhizomedb.entityOnNonUniqueAttribute
import com.jetbrains.rhizomedb.exists
import com.jetbrains.rhizomedb.get
import com.jetbrains.rhizomedb.queryIndex
import com.jetbrains.rhizomedb.single
import fleet.kernel.DurableEntityType
import fleet.kernel.FrontendPart
import fleet.kernel.KernelContextElement
import fleet.kernel.SharedPart
import fleet.kernel.Transactor
import fleet.kernel.WorkspacePart
import fleet.kernel.byUidOrNull
import fleet.kernel.change
import fleet.kernel.deprecatedUid
import fleet.kernel.lastKnownDb
import fleet.kernel.rebase.DefaultInstructionSet
import fleet.kernel.rebase.FollowerTransactorMiddleware
import fleet.kernel.rebase.LeaderTransactorMiddleware
import fleet.kernel.rebase.RemoteKernelImpl
import fleet.kernel.rebase.UniversalInstruction
import fleet.kernel.rebase.await
import fleet.kernel.rebase.awaitCommitted
import fleet.kernel.rebase.causal
import fleet.kernel.rebase.initWorkspaceClock
import fleet.kernel.rebase.shared
import fleet.kernel.rebase.sharedRead
import fleet.kernel.rebase.withRebaseLoop
import fleet.kernel.rebase.withTransactorView
import fleet.kernel.ref
import fleet.kernel.rete.asValuesFlow
import fleet.kernel.rete.query
import fleet.kernel.rete.withRete
import fleet.kernel.waitForNotNull
import fleet.kernel.withTransactor
import fleet.util.Causal
import fleet.util.UID
import fleet.util.async.withCoroutineScope
import fleet.util.serialization.PersistentListSerializer
import fleet.util.singleOrNullOrThrow
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RebaseLoopTest {

  @Serializable
  data class AppendUID(
    val uid: UID,
    override val seed: Long = Random.nextLong(),
  ) : UniversalInstruction {
    companion object {
      object Coder : UniversalInstruction.IdentityCoder<AppendUID>(AppendUID::class, serializer(), "AppendUID")
    }

    override fun DbContext<Q>.expand(): InstructionExpansion {
      val (entity, v) = VectorEntity.Vector.all().single()
      return InstructionExpansion(listOf(Op.Assert(entity.eid, VectorEntity.Vector.attr, v.add(uid))))
    }
  }

  @Serializable
  data class IncCounter(override val seed: Long = Random.nextLong()) : UniversalInstruction {
    companion object {
      object Coder : UniversalInstruction.IdentityCoder<IncCounter>(IncCounter::class, serializer(), "IncCounter")
    }

    override fun DbContext<Q>.expand(): InstructionExpansion =
      TestCounter.CounterAttr.attr.let { attribute ->
        val datom = queryIndex(IndexQuery.Column(attribute)).single()
        InstructionExpansion(listOf(Op.Assert(datom.eid, attribute, (datom.value as Long) + 1)))
      } ?: InstructionExpansion(emptyList())
  }

  data class VectorEntity(override val eid: EID) : Entity {
    companion object : DurableEntityType<VectorEntity>(VectorEntity::class, ::VectorEntity) {
      val Vector = requiredValue("vector", PersistentListSerializer(UID.serializer())) { persistentListOf() }
    }

    val vector by Vector
  }

  data class TestCounter(override val eid: EID) : Entity {
    val counter: Long by CounterAttr

    companion object : DurableEntityType<TestCounter>(TestCounter::class, ::TestCounter) {
      val CounterAttr = requiredValue("counter", Long.serializer())
    }
  }

  private suspend fun waitFor(condition: () -> Boolean) {
    query { condition() }.asValuesFlow().first { satisfied -> satisfied }
  }

  private fun rebaseLoopTest(body: suspend CoroutineScope.() -> Unit) {
    runBlocking(Dispatchers.Default) {
      withTimeout(20.seconds) {
        body()
      }
    }
  }

  private fun runBlockingWithNKernels(
    numKernels: Int,
    workspaceSetup: ChangeScope.() -> Unit = {},
    block: suspend CoroutineScope.(Transactor, List<Transactor>) -> Unit,
  ) = rebaseLoopTest {
    val instructionSet =
      DefaultInstructionSet + IncCounter.Companion.Coder + AppendUID.Companion.Coder + FailsIfEven.Companion.Coder
    withTransactor(middleware = LeaderTransactorMiddleware(instructionSet.encoder()),
                   registerEntityTypeOnEntityCreation = true) {
      change {
        initWorkspaceClock()
      }
      withTransactorView(hiddenPart = FrontendPart, defaultPart = WorkspacePart) { workspaceKernel ->
        withRete {
          withCoroutineScope { pluginScope ->
            val setupCausal = causal(change {
              register(VectorEntity)
              register(TestCounter)
              register(TestSharedEntity)
              register(TestSharedEntityWithDependency)
              register(TestSharedRetractableEntity)
              register(TestSharedEntityWithRelation)
              register(SharedRetractableEntity)
              register(SharedIndexedEntity)
              register(SharedEntityWithUniqueValue)
              register(TestLocalEntityWithCascadeDeleteBy)
              register(TestLocalEntityReferencingSharedOne)
              register(TestLocalEntity)
              register(TestLocalRetractableEntity)
              register(LocalWorkspaceEntity)
              register(EntityWithIndexed)
              register(RetractableEntity1)
              register(RetractableEntity2)
              register(ADurableEntity)
              workspaceSetup()
            })


            suspend fun loop(
              out: ArrayList<Transactor> = ArrayList(),
              n: Int = numKernels,
            ) {
              when {
                n == 0 -> coroutineScope { block(workspaceKernel, out) }
                else -> {
                  withTransactor(middleware = FollowerTransactorMiddleware(instructionSet.encoder()),
                                 registerEntityTypeOnEntityCreation = true,
                                 defaultPart = FrontendPart) { k ->
                    withRete {
                      change {
                        register(VectorEntity)
                        register(TestCounter)
                        register(TestSharedEntity)
                        register(TestSharedEntityWithDependency)
                        register(TestSharedRetractableEntity)
                        register(TestSharedEntityWithRelation)
                        register(SharedRetractableEntity)
                        register(SharedIndexedEntity)
                        register(SharedEntityWithUniqueValue)
                        register(TestLocalEntityWithCascadeDeleteBy)
                        register(TestLocalEntityReferencingSharedOne)
                        register(TestLocalEntity)
                        register(TestLocalRetractableEntity)
                        register(LocalWorkspaceEntity)
                        register(EntityWithIndexed)
                        register(RetractableEntity1)
                        register(RetractableEntity2)
                        register(ADurableEntity)
                      }
                      withRebaseLoop(
                        remoteKernel = CompletableDeferred(RemoteKernelImpl(workspaceKernel, pluginScope, instructionSet.decoder())),
                        instructionSet = instructionSet,
                        reconnectWhenBroken = false
                      ) {
                        setupCausal.await()
                        coroutineScope {
                          out.add(k)
                          loop(out, n - 1)
                        }
                      }
                    }
                  }
                }
              }
            }
            loop()
          }
        }
      }
    }
  }

  @Serializable
  data class FailsIfEven(override val seed: Long = Random.nextLong()) : UniversalInstruction {
    companion object {
      object Coder : UniversalInstruction.IdentityCoder<FailsIfEven>(FailsIfEven::class, serializer(), "FailsIfEven")
    }

    override fun DbContext<Q>.expand(): InstructionExpansion {
      if (TestCounter.single().counter % 2 == 0L) {
        error("some error")
      }
      return InstructionExpansion(emptyList())
    }
  }

  // todo: jetzajac
  @Ignore
  @Test
  fun `failing instruction`() {
    val n = 10
    val ksN = 2
    return runBlockingWithNKernels(ksN, workspaceSetup = {
      withDefaultPart(SharedPart) {
        TestCounter.new {
          it[TestCounter.CounterAttr] = 0L
        }
      }
    }) { wsKernel, ks ->
      ks.forEach { k ->
        launch(KernelContextElement(k)) {
          waitFor { TestCounter.singleOrNull() != null }
          repeat(n) {
            change {
              shared {
                mutate(IncCounter())
              }
            }

            change {
              if (TestCounter.single().counter % 2L != 0L) {
                shared {
                  mutate(FailsIfEven())
                }
              }
            }
          }
          awaitCommitted()
        }
      }

      withContext(KernelContextElement(wsKernel)) {
        query {
          TestCounter.single().counter
        }.asValuesFlow().first { c ->
          println("$c")
          c == (ksN * n).toLong()
        }
      }
    }
  }

  @Test
  fun `interleaved mutations with assumptions and without ones`() {
    val numKernels = 10
    val numTxs = 20
    runBlockingWithNKernels(numKernels, workspaceSetup = {
      withDefaultPart(SharedPart) {
        TestCounter.new {
          it[TestCounter.CounterAttr] = 0L
        }
      }
    }) { wsKernel, kernels ->
      val expectedCounter = numKernels * numTxs.toLong() * 3
      val sagas = kernels.map { kernel ->
        async {
          withContext(KernelContextElement(kernel)) {
            waitFor { TestCounter.singleOrNull() != null }
            for (i in 0 until numTxs) {
              change {
                shared {
                  mutate(IncCounter())
                }
              }

              change {
                val counter = TestCounter.single()
                shared {
                  // will assume the prev counter value
                  counter[TestCounter.CounterAttr]++
                }
              }

              change {
                shared {
                  mutate(IncCounter())
                }
              }
            }
            kernel.dbSource.flow
              .map { db -> asOf(db) { TestCounter.all().first().counter } }
              .first { c -> c == expectedCounter }
            change {
              sharedRead {
                TestCounter.all().first().counter
              }
            }.let {
              awaitCommitted()
              it.value
            }
          }
        }

      }
      val counters = sagas.awaitAll()
      counters.forEach { c ->
        assertEquals(expectedCounter, c)
      }
    }
  }

  @Test
  fun `awaitCommitted when no instructions sent`() {
    runBlockingWithNKernels(1) { _, (kernel) ->
      val x = withContext(KernelContextElement(kernel)) {
        change {
          sharedRead {
            1
          }
        }.let {
          awaitCommitted()
          it.value
        }
      }
      assertEquals(1, x)
    }
  }

  @Test
  fun `awaitCommitted for sent instruction`() {
    runBlockingWithNKernels(1) { _, (kernel) ->
      val uid = UID.random()
      val v = withContext(KernelContextElement(kernel)) {
        val e = change {
          shared {
            val e = VectorEntity.new()
            mutate(AppendUID(uid))
            e
          }
        }
        awaitCommitted()
        e.vector
      }
      assertEquals(listOf(uid), v)
    }
  }

  @Test
  fun `rebase user effects`() {
    rebaseStressTest(numKernels = 10, numTxs = 10, checkUserEffects = true)
  }

  @Test
  fun `rebase test`() {
    rebaseStressTest(numKernels = 10, numTxs = 10, checkUserEffects = false)
  }

  @Suppress("SameParameterValue")
  private fun rebaseStressTest(numKernels: Int, numTxs: Int, checkUserEffects: Boolean) {
    val workspaceSetup: ChangeScope.() -> Unit = {
      shared {
        VectorEntity.new()
      }
    }
    runBlockingWithNKernels(
      workspaceSetup = workspaceSetup,
      numKernels = numKernels
    ) { _, kernels ->
      val sagas = kernels.map { kernel ->
        async {
          withContext(KernelContextElement(kernel)) {
            waitFor { VectorEntity.singleOrNull() != null }
            val userEffects = mutableListOf<UID>()
            for (i in 0 until numTxs) {
              change {
                val uid = UID.random()
                shared {
                  effect { userEffects.add(uid) }
                  mutate(AppendUID(uid))
                }
              }
            }
            kernel.dbSource.flow
              .map { db -> asOf(db) { VectorEntity.Vector.single().second } }
              .first { v -> v.size == numKernels * numTxs }
            awaitCommitted()
            if (checkUserEffects) {
              assertEquals(numTxs, userEffects.size)
            }
          }
        }
      }
      val vectors = sagas.awaitAll()
      assertTrue(vectors.zipWithNext().all { (v1, v2) ->
        v1 == v2
      })
    }
  }

  @Test
  fun `pass causal from frontend to workspace`() {
    runBlockingWithNKernels(
      1,
      workspaceSetup = {
        withDefaultPart(SharedPart) {
          TestCounter.new {
            it[TestCounter.CounterAttr] = 0L
          }
        }
      }) { workspaceKernel, (clientKernel) ->
      val causalsChannel = Channel<Causal<Long>>(UNLIMITED)
      val wsSaga = async {
        withContext(KernelContextElement(workspaceKernel)) {
          causalsChannel.consumeEach { causal ->
            val c = causal.await()
            val cc = TestCounter.all().singleOrNullOrThrow()
            assertNotNull(cc, "counter should have already be present after await")
            assertTrue("causal: $c db: ${cc.counter}") { c <= cc.counter }
          }

        }
      }
      val clientSaga = async {
        withContext(KernelContextElement(clientKernel)) {
          waitFor { TestCounter.singleOrNull() != null }

          for (i in 0 until 100) {
            change {
              shared {
                mutate(IncCounter())
              }
            }
            causalsChannel.send(causal(TestCounter.single().counter))
          }
          causalsChannel.close()

        }
      }
      clientSaga.await()
      wsSaga.await()
    }
  }

  @Test
  fun `pass causal from workspace to frontend`() {
    runBlockingWithNKernels(1) { workspaceKernel, (clientKernel) ->
      val causalsChannel = Channel<Causal<Long>>(UNLIMITED)
      val clientSaga = async {
        withContext(KernelContextElement(clientKernel)) {
          causalsChannel.consumeEach { causal ->
            val c = causal.await()
            val cc = TestCounter.all().singleOrNullOrThrow()
            assertNotNull(cc, "counter should have already be present after await")
            assertTrue("causal: $c db: ${cc.counter}") { c <= cc.counter }
          }

        }
      }
      val wsSaga = async {
        withContext(KernelContextElement(workspaceKernel)) {
          val counter = change {
            withDefaultPart(SharedPart) {
              TestCounter.new {
                it[TestCounter.CounterAttr] = 0L
              }
            }
          }
          for (i in 0 until 100) {
            change {
              counter[TestCounter.CounterAttr]++
            }
            causalsChannel.send(causal(counter.counter))
          }
          causalsChannel.close()
        }
      }
      clientSaga.await()
      wsSaga.await()
    }
  }

  data class TestSharedEntity(override val eid: EID) : Entity {
    val optionalString: String? by OptionalStringAttr

    companion object : DurableEntityType<TestSharedEntity>(TestSharedEntity::class, ::TestSharedEntity) {
      val OptionalStringAttr = optionalValue("optionalString", String.serializer())
    }
  }

  data class TestSharedEntityWithDependency(override val eid: EID) : Entity {
    val dependency: Entity by DependencyAttr

    companion object : DurableEntityType<TestSharedEntityWithDependency>(
      TestSharedEntityWithDependency::class,
      ::TestSharedEntityWithDependency
    ) {
      val DependencyAttr = requiredRef<Entity>("dependency", RefFlags.CASCADE_DELETE_BY)
    }
  }

  data class TestLocalEntityWithCascadeDeleteBy(override val eid: EID) : Entity {
    val sharedOne: TestSharedEntity by SharedOneAttr

    companion object : EntityType<TestLocalEntityWithCascadeDeleteBy>(
      TestLocalEntityWithCascadeDeleteBy::class,
      ::TestLocalEntityWithCascadeDeleteBy
    ) {
      val SharedOneAttr = requiredRef<TestSharedEntity>("sharedOne", RefFlags.CASCADE_DELETE_BY)
    }
  }

  fun <T> testSharedEntityRetraction(localTx: ChangeScope.(TestSharedEntity) -> T, check: (T) -> Unit) {
    runBlockingWithNKernels(1) { workspaceKernel, (clientKernel) ->
      val clientChanged = CompletableDeferred<Causal<Unit>>()
      val workspaceChanged = CompletableDeferred<Causal<Unit>>()
      val clientSaga = async {
        withContext(KernelContextElement(clientKernel)) {
          val t = change {
            val shared = shared {
              TestSharedEntity.new {}
            }
            localTx(shared)
          }
          clientChanged.complete(causal(Unit))
          workspaceChanged.await().await()
          check(t)
        }
      }
      val workspaceSaga = async {
        withContext(KernelContextElement(workspaceKernel)) {
          clientChanged.await().await()
          val shared = TestSharedEntity.all().single()
          change {
            shared.delete()
          }
          workspaceChanged.complete(causal(Unit))
        }
      }
      workspaceSaga.await()
      clientSaga.await()
    }
  }

  @Test
  fun `local entity referencing shared one is retracted by transaction from workspace`() {
    repeat(100) {
      testSharedEntityRetraction(
        localTx = { shared ->
          TestLocalEntityWithCascadeDeleteBy.new {
            it[TestLocalEntityWithCascadeDeleteBy.SharedOneAttr] = shared
          }
        },
        check = { local ->
          if (local.exists()) {
            println(local.sharedOne)
          }
          assertFalse(local.exists())
        })
    }
  }

  data class TestLocalEntityReferencingSharedOne(override val eid: EID) : Entity {
    val sharedOne: TestSharedEntity? by SharedOneAttr

    companion object : EntityType<TestLocalEntityReferencingSharedOne>(
      TestLocalEntityReferencingSharedOne::class,
      ::TestLocalEntityReferencingSharedOne
    ) {
      val SharedOneAttr = optionalRef<TestSharedEntity>("sharedOne")
    }
  }

  @Test
  fun `local entity referencing shared one loses the reference by transaction from workspace`() {
    testSharedEntityRetraction(
      localTx = { shared ->
        TestLocalEntityReferencingSharedOne.new {
          it[TestLocalEntityReferencingSharedOne.SharedOneAttr] = shared
        }
      },
      check = { local ->
        assertNull(local.sharedOne)
      })
  }

  // the deliberately failing change makes Transactor log an error, which the JUnit5 bazel runner reports as a test failure
  @Ignore
  @Test
  fun `reading local entities in shared scope does not break everything`() {
    val lastUid = UID.random()
    runBlockingWithNKernels(1) { workspaceKernel, (clientKernel) ->
      withContext(KernelContextElement(clientKernel)) {
        change {
          shared {
            VectorEntity.new()
          }
          TestLocalEntity.new {
            it[TestLocalEntity.XAttr] = 0
          }
        }
        awaitCommitted()
        try {
          change {
            shared {
              mutate(AppendUID(UID.random()))
            }
            shared {
              // the local partition is hidden from the shared scope
              TestLocalEntity.single()[TestLocalEntity.XAttr]++
            }
            shared {
              mutate(AppendUID(UID.random()))
            }
          }
        } catch (ignore: Exception) {
        }
        change {
          shared {
            mutate(AppendUID(lastUid))
          }
        }
      }
      val clientVector = clientKernel.dbSource.flow.map { db ->
        asOf(db) { VectorEntity.Vector.single().second }
      }.first { v -> v.contains(lastUid) }

      val workspaceVector = workspaceKernel.dbSource.flow.map { db ->
        asOf(db) { VectorEntity.Vector.single().second }
      }.first { v -> v.contains(lastUid) }

      println("clientVector: $clientVector")
      println("workspaceVector: $workspaceVector")
      assertEquals(clientVector, workspaceVector)
    }
  }

  data class TestLocalEntity(override val eid: EID) : Entity {
    val testSharedEntity: TestSharedEntity? by TestSharedEntityAttr
    val x: Long by XAttr

    companion object : EntityType<TestLocalEntity>(TestLocalEntity::class, ::TestLocalEntity) {
      val TestSharedEntityAttr = optionalRef<TestSharedEntity>("testSharedEntity")
      val XAttr = requiredValue("x", Long.serializer())
    }
  }

  data class TestLocalRetractableEntity(override val eid: EID) : RetractableEntity, Entity {
    val testSharedEntity by TestSharedEntityAttr
    val y by YAttr

    override fun onRetract(): RetractableEntity.Callback {
      val lookupOne = entityOnNonUniqueAttribute(TestLocalEntity.TestSharedEntityAttr, testSharedEntity)
      val y = y
      return RetractableEntity.Callback {
        lookupOne!![TestLocalEntity.XAttr] += y
      }
    }

    companion object :
      EntityType<TestLocalRetractableEntity>(TestLocalRetractableEntity::class, ::TestLocalRetractableEntity) {
      val TestSharedEntityAttr = requiredRef<TestSharedEntity>("testSharedEntity", RefFlags.CASCADE_DELETE_BY)
      val YAttr = requiredValue("y", Long.serializer())
    }
  }

  data class SharedRetractableEntity(override val eid: EID) : RetractableEntity {
    companion object :
      DurableEntityType<SharedRetractableEntity>(SharedRetractableEntity::class, ::SharedRetractableEntity) {
      val Ref = requiredRef<TestSharedEntity>("ref")
    }

    override fun onRetract(): RetractableEntity.Callback {
      val ref = this[Ref]
      return RetractableEntity.Callback {
        println("hello?")
        ref[TestSharedEntity.OptionalStringAttr] += " hello from workspace"
      }
    }
  }

  @Test
  fun `effects from leader are broadcasted to follower`() = runBlockingWithNKernels(1) { workspace, (client) ->
    val sharedEntityRef = withContext(KernelContextElement(workspace)) {
      val (sharedEntity, retractable) =
        change {
          val sharedEntity = shared {
            TestSharedEntity.new {
              it[TestSharedEntity.OptionalStringAttr] = "empty"
            }
          }
          val retractableEntity = SharedRetractableEntity.new {
            it[SharedRetractableEntity.Ref] = sharedEntity
          }
          sharedEntity to retractableEntity
        }

      change {
        retractable.delete()
      }
      causal(sharedEntity.ref())
    }

    withContext(KernelContextElement(client)) {
      assertEquals("empty hello from workspace", sharedEntityRef.await().deref().optionalString)
    }
  }

  @Test
  fun `test retract retractable entity correctly`() {
    runBlockingWithNKernels(1) { workspaceKernel, (clientKernel) ->

      clientKernel.changeSuspend {
        val shared = shared {
          TestSharedEntity.new {}
        }
        TestLocalEntity.new {
          it[TestLocalEntity.TestSharedEntityAttr] = shared
          it[TestLocalEntity.XAttr] = 10
        }
        TestLocalRetractableEntity.new {
          it[TestLocalRetractableEntity.TestSharedEntityAttr] = shared
          it[TestLocalRetractableEntity.YAttr] = 20
        }
      }
      withContext(KernelContextElement(workspaceKernel)) {
        waitFor { TestSharedEntity.all().isNotEmpty() }
      }
      //val changeAsync = async {
      //  clientKernel.changes().consumeAsFlow().first()
      //}
      workspaceKernel.changeSuspend {
        TestSharedEntity.all().single().delete()
      }
      //val change = changeAsync.await()
      withContext(KernelContextElement(clientKernel)) {
        waitFor { TestLocalEntity.singleOrNull()?.x == 30L }
      }
    }
  }

  @Test
  fun `test retract shared entity correctly`() {
    runBlockingWithNKernels(1) { workspaceKernel, (clientKernel) ->
      val (testUid, dependantUid) = withContext(KernelContextElement(workspaceKernel)) {
        change {
          val test = withDefaultPart(SharedPart) { TestSharedEntity.new {} }
          val d = withDefaultPart(SharedPart) {
            TestSharedEntityWithDependency.new {
              it[TestSharedEntityWithDependency.DependencyAttr] = test
            }
          }
          test.deprecatedUid() to d.deprecatedUid()
        }
      }
      val sharedEntity = withContext(KernelContextElement(clientKernel)) {
        waitForNotNull { byUidOrNull<TestSharedEntityWithDependency>(dependantUid) }
      }
      launch {
        withContext(KernelContextElement(clientKernel)) {
          change {
            shared {
              if (sharedEntity.exists()) {
                TestSharedEntityWithDependency.new {
                  it[TestSharedEntityWithDependency.DependencyAttr] = sharedEntity
                }
              }
            }
          }
        }
      }
      launch {
        withContext(KernelContextElement(workspaceKernel)) {
          change {
            byUidOrNull<TestSharedEntity>(testUid)?.delete()
          }
        }
      }
      withContext(KernelContextElement(workspaceKernel)) {
        waitFor { byUidOrNull<TestSharedEntity>(testUid) == null }
      }
    }
  }

  data class TestSharedRetractableEntity(override val eid: EID) : RetractableEntity, Entity {
    val sharedCounter by SharedCounterAttr

    override fun onRetract(): RetractableEntity.Callback {
      val counter = sharedCounter
      return RetractableEntity.Callback {
        counter[TestCounter.CounterAttr] = counter.counter - 1
      }
    }

    companion object : DurableEntityType<TestSharedRetractableEntity>(
      TestSharedRetractableEntity::class,
      ::TestSharedRetractableEntity
    ) {
      val SharedCounterAttr = requiredRef<TestCounter>("sharedCounter")
    }
  }

  @Test
  fun `test shared entity retraction`() {
    runBlockingWithNKernels(1, workspaceSetup = {
      val counter = withDefaultPart(SharedPart) {
        TestCounter.new {
          it[TestCounter.CounterAttr] = 2
        }
      }
      withDefaultPart(SharedPart) {
        TestSharedRetractableEntity.new {
          it[TestSharedRetractableEntity.SharedCounterAttr] = counter
        }
      }
      withDefaultPart(SharedPart) {
        TestSharedRetractableEntity.new {
          it[TestSharedRetractableEntity.SharedCounterAttr] = counter
        }
      }
    }) { workspaceKernel, (clientKernel) ->
      withContext(KernelContextElement(clientKernel)) {
        waitFor { TestCounter.singleOrNull() != null }
        change {
          shared {
            TestSharedRetractableEntity.all().forEach {
              it.delete()
            }
          }
        }
        awaitCommitted()
      }
      asOf(workspaceKernel.lastKnownDb) {
        assertEquals(0, TestCounter.single().counter)
      }
    }
  }

  data class TestSharedEntityWithRelation(override val eid: EID) : Entity {
    val sharedEntity: TestSharedEntity? by SharedEntityAttr

    companion object : DurableEntityType<TestSharedEntityWithRelation>(
      TestSharedEntityWithRelation::class,
      ::TestSharedEntityWithRelation
    ) {
      val SharedEntityAttr = optionalRef<TestSharedEntity>("sharedEntity")
    }
  }

  @Test
  fun `querying speculatively created entity`() {
    runBlockingWithNKernels(1) { workspaceKernel, (clientKernel) ->
      // issuing really long change to make sure workspace won't acknowledge entity creation
      workspaceKernel.changeAsync { Thread.sleep(1000) }
      withContext(KernelContextElement(clientKernel)) {
        val deletedSharedEntity = change {
          val e = shared {
            val e = TestSharedEntity.new {}
            TestSharedEntityWithRelation.new {
              it[TestSharedEntityWithRelation.SharedEntityAttr] = e
            }
            e
          }
          shared {
            e.delete()
          }
          e
        }
        change {
          shared {
            entities(TestSharedEntityWithRelation.SharedEntityAttr, deletedSharedEntity)
            deletedSharedEntity.exists()
          }
        }
        awaitCommitted()
      }
    }
  }

  data class SharedIndexedEntity(override val eid: EID) : Entity {
    val index: Long by IndexAttr

    companion object : DurableEntityType<SharedIndexedEntity>(SharedIndexedEntity::class, ::SharedIndexedEntity) {
      val IndexAttr = requiredValue("index", Long.serializer(), Indexing.UNIQUE)
    }
  }

  @Test
  fun `only one kernel creates entity`() {
    runBlockingWithNKernels(10) { workspaceKernel, kernels ->
      val uids = kernels.map {
        async {
          withContext(KernelContextElement(it)) {
            change {
              shared {
                if (entity(SharedIndexedEntity.IndexAttr, 1L) == null) {
                  SharedIndexedEntity.new {
                    it[SharedIndexedEntity.IndexAttr] = 1L
                  }
                }
              }
            }
            awaitCommitted()
            entity(SharedIndexedEntity.IndexAttr, 1L)?.deprecatedUid()
          }
        }
      }
      assertEquals(1, uids.map { it.await() }.distinct().size)
    }
  }

  data class SharedEntityWithUniqueValue(override val eid: EID) : Entity {
    val i: Int by IAttr
    val x: Int by XAttr

    companion object : DurableEntityType<SharedEntityWithUniqueValue>(
      SharedEntityWithUniqueValue::class,
      ::SharedEntityWithUniqueValue
    ) {
      val IAttr = requiredValue("i", Int.serializer(), Indexing.UNIQUE)
      val XAttr = requiredValue("x", Int.serializer())
    }
  }

  data class LocalWorkspaceEntity(override val eid: EID) : Entity {
    val e: SharedEntityWithUniqueValue by EAttr

    companion object : EntityType<LocalWorkspaceEntity>(LocalWorkspaceEntity::class, ::LocalWorkspaceEntity) {
      val EAttr = requiredRef<SharedEntityWithUniqueValue>("e", RefFlags.CASCADE_DELETE, RefFlags.CASCADE_DELETE_BY)
    }
  }

  @Test
  fun `cascade delete on workspace doesn't brick the program`() {
    runBlockingWithNKernels(1) { workspaceKernel, (clientKernel) ->
      workspaceKernel.changeAsync {
        val s = withDefaultPart(SharedPart) {
          SharedEntityWithUniqueValue.new {
            it[SharedEntityWithUniqueValue.IAttr] = 1
            it[SharedEntityWithUniqueValue.XAttr] = 1
          }
        }
        LocalWorkspaceEntity.new {
          it[LocalWorkspaceEntity.EAttr] = s
        }
      }
      withContext(KernelContextElement(clientKernel)) {
        waitFor { SharedEntityWithUniqueValue.singleOrNull()?.x == 1 }
      }

      workspaceKernel.changeAsync {
        LocalWorkspaceEntity.single().delete()
      }

      workspaceKernel.changeAsync {
        withDefaultPart(SharedPart) {
          SharedEntityWithUniqueValue.new {
            it[SharedEntityWithUniqueValue.IAttr] = 1
            it[SharedEntityWithUniqueValue.XAttr] = 2
          }
        }
      }

      withContext(KernelContextElement(clientKernel)) {
        waitFor { SharedEntityWithUniqueValue.singleOrNull()?.x == 2 }
      }
    }
  }

  @Test
  fun `cascade delete from the local partition on a frontend`() {
    runBlockingWithNKernels(
      numKernels = 2,
      workspaceSetup = {
        withDefaultPart(SharedPart) {
          SharedEntityWithUniqueValue.new {
            it[SharedEntityWithUniqueValue.IAttr] = -1
            it[SharedEntityWithUniqueValue.XAttr] = -1
          }
        }
      }) { workspaceKernel, clients ->
      coroutineScope {
        clients.forEachIndexed { i, c ->
          launch(KernelContextElement(c)) {
            val es = (0..100).map { j ->
              change {
                LocalWorkspaceEntity.new {
                  it[LocalWorkspaceEntity.EAttr] = shared {
                    SharedEntityWithUniqueValue.new {
                      it[SharedEntityWithUniqueValue.IAttr] = 2 * j + i
                      it[SharedEntityWithUniqueValue.XAttr] = i
                    }
                  }
                }
              }
            }
            es.forEach { e ->
              change {
                e.delete()
              }
            }
            awaitCommitted()
          }
        }
      }

      withContext(KernelContextElement(workspaceKernel)) {
        assertEquals(-1, SharedEntityWithUniqueValue.single().i)
      }
    }
  }

  @Test
  fun `local effect reads local partition on follower`() {
    runBlockingWithNKernels(1) { workspaceKernel, (clientKernel) ->
      withContext(KernelContextElement(clientKernel)) {
        change {
          val shared = shared {
            TestSharedEntity.new {}
          }
          val display = TestLocalEntity.new {
            it[TestLocalEntity.TestSharedEntityAttr] = shared
            it[TestLocalEntity.XAttr] = 1
          }
          TestLocalRetractableEntity.new {
            it[TestLocalRetractableEntity.TestSharedEntityAttr] = shared
            it[TestLocalRetractableEntity.YAttr] = 20
          }
          shared {
            shared.delete()
          }
          assertEquals(21, display.x)
        }
      }
    }
  }

  data class EntityWithIndexed(override val eid: EID) : Entity {
    companion object : EntityType<EntityWithIndexed>(EntityWithIndexed::class, ::EntityWithIndexed) {
      val Indexed = requiredValue("Indexed", Int.serializer(), Indexing.INDEXED)
    }
  }

  data class RetractableEntity1(override val eid: EID) : RetractableEntity {
    companion object : EntityType<RetractableEntity1>(RetractableEntity1::class, ::RetractableEntity1)

    override fun onRetract(): RetractableEntity.Callback? {
      return RetractableEntity.Callback {
        entities(EntityWithIndexed.Indexed, 1).forEach { it.delete() }
      }
    }
  }

  data class RetractableEntity2(override val eid: EID) : RetractableEntity {
    companion object : EntityType<RetractableEntity2>(RetractableEntity2::class, ::RetractableEntity2) {
      val cascadeDeleteBy = optionalRef<Entity>("cascadeDeleteBy", RefFlags.CASCADE_DELETE_BY)
      val Ref = optionalRef<EntityWithIndexed>("localRef")
    }

    override fun onRetract(): RetractableEntity.Callback? {
      val ref = this[Ref]!!
      return RetractableEntity.Callback {
        println(ref[EntityWithIndexed.Indexed])
      }
    }
  }

  @Test
  fun `effects in short circuit mode don't see the hidden partition`() = rebaseLoopTest {
    withTransactor(middleware = LeaderTransactorMiddleware(DefaultInstructionSet.encoder()),
                   registerEntityTypeOnEntityCreation = true) { commonKernel ->
      val frontendEntty = change {
        withDefaultPart(FrontendPart) {
          register(EntityWithIndexed)
          EntityWithIndexed.new {
            it[EntityWithIndexed.Indexed] = 1
          }
        }
      }

      withTransactorView(hiddenPart = FrontendPart, defaultPart = WorkspacePart) {
        change {
          register(RetractableEntity1)
          RetractableEntity1.new {}.delete()
          //            effect {
          //              entities(EntityWithIndexed.Indexed, 1).forEach { it.delete() }
          //            }
        }
      }
      assertTrue(frontendEntty.exists())
    }
  }

  @Test
  fun `local effect reads local partition on leader`() {
    runBlockingWithNKernels(1) { workspaceKernel, (clientKernel) ->
      withContext(KernelContextElement(workspaceKernel)) {
        change {
          val shared = shared {
            TestSharedEntity.new {}
          }
          val display = TestLocalEntity.new {
            it[TestLocalEntity.TestSharedEntityAttr] = shared
            it[TestLocalEntity.XAttr] = 1
          }
          TestLocalRetractableEntity.new {
            it[TestLocalRetractableEntity.TestSharedEntityAttr] = shared
            it[TestLocalRetractableEntity.YAttr] = 20
          }
          shared {
            shared.delete()
          }
          assertEquals(21, display.x)
        }
      }
    }
  }

  data class ADurableEntity(override val eid: EID) : Entity {
    companion object : DurableEntityType<ADurableEntity>(ADurableEntity::class, ::ADurableEntity)
  }

  @Test
  fun `cascade delete to local partition from shared in short circuit`() = rebaseLoopTest {
    withTransactor(middleware = LeaderTransactorMiddleware(DefaultInstructionSet.encoder()),
                   registerEntityTypeOnEntityCreation = true) { commonKernel ->
      change {
        initWorkspaceClock()
        register(RetractableEntity2)
        register(ADurableEntity)
        register(EntityWithIndexed)
      }
      val (someSharedEntity, someFrontendEntity) = withTransactorView(
        hiddenPart = WorkspacePart,
        defaultPart = FrontendPart
      ) {
        change {
          val sharedEntity = shared {
            ADurableEntity.new()
          }

          val frontendEntity = RetractableEntity2.new {
            it[RetractableEntity2.Ref] = EntityWithIndexed.new {
              it[EntityWithIndexed.Indexed] = 1
            }
            it[RetractableEntity2.cascadeDeleteBy] = sharedEntity
          }

          sharedEntity to frontendEntity
        }
      }

      withTransactorView(hiddenPart = FrontendPart, defaultPart = WorkspacePart) {
        change {
          assertTrue(someSharedEntity.exists())
          someSharedEntity.delete()
        }
      }
      withTransactorView(hiddenPart = WorkspacePart, defaultPart = FrontendPart) {
        assertFalse(someFrontendEntity.exists())
      }
    }
  }
}
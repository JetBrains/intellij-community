package fleet.kernel.rebase.test

import com.jetbrains.rhizomedb.DbContext
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.InstructionExpansion
import com.jetbrains.rhizomedb.Op
import com.jetbrains.rhizomedb.Q
import com.jetbrains.rhizomedb.all
import com.jetbrains.rhizomedb.asOf
import fleet.kernel.DurableEntityType
import fleet.kernel.FrontendPart
import fleet.kernel.KernelContextElement
import fleet.kernel.Transactor
import fleet.kernel.WorkspacePart
import fleet.kernel.change
import fleet.kernel.rebase.DefaultInstructionSet
import fleet.kernel.rebase.FollowerTransactorMiddleware
import fleet.kernel.rebase.LeaderTransactorMiddleware
import fleet.kernel.rebase.RemoteKernel
import fleet.kernel.rebase.RemoteKernelImpl
import fleet.kernel.rebase.SharedChangeScope
import fleet.kernel.rebase.UniversalInstruction
import fleet.kernel.rebase.await
import fleet.kernel.rebase.causal
import fleet.kernel.rebase.initWorkspaceClock
import fleet.kernel.rebase.shared
import fleet.kernel.rebase.withRebaseLoop
import fleet.kernel.rebase.withTransactorView
import fleet.kernel.withTransactor
import fleet.rpc.remoteApiDescriptor
import fleet.util.UID
import fleet.util.async.withCoroutineScope
import fleet.util.serialization.PersistentListSerializer
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class RebaseLoopReconnectTest {

  data class VectorEntity(override val eid: EID) : Entity {
    companion object : DurableEntityType<VectorEntity>(VectorEntity::class, ::VectorEntity) {
      val Vector = requiredValue("vector", PersistentListSerializer(UID.serializer())) { persistentListOf() }
    }

    val vector: PersistentList<UID> by Vector
  }

  @Serializable
  data class AppendUID(
    val uid: UID,
    override val seed: Long = Random.nextLong(),
  ) : UniversalInstruction {
    companion object {
      object Coder : UniversalInstruction.IdentityCoder<AppendUID>(AppendUID::class, serializer(), "AppendUID")
    }

    override fun DbContext<Q>.expand(): InstructionExpansion {
      val (entity, vector) = VectorEntity.Vector.all().single()
      return InstructionExpansion(listOf(Op.Assert(entity.eid, VectorEntity.Vector.attr, vector.add(uid))))
    }
  }

  private fun SharedChangeScope.addUid(uid: UID) {
    VectorEntity.all().firstOrNull() ?: VectorEntity.new()
    mutate(AppendUID(uid))
  }

  private fun vectorFlow(transactor: Transactor): Flow<PersistentList<UID>> =
    transactor.dbSource.flow.mapNotNull { db -> asOf(db) { VectorEntity.singleOrNull()?.vector } }

  @Test
  fun `rebase loop reconnects`() = runBlocking(Dispatchers.Default) {
    withTimeout(60.seconds) {
      val instructionSet = DefaultInstructionSet + AppendUID.Companion.Coder
      withTransactor(middleware = LeaderTransactorMiddleware(instructionSet.encoder()),
                     registerEntityTypeOnEntityCreation = true) {
        change {
          initWorkspaceClock()
        }
        withTransactorView(hiddenPart = FrontendPart, defaultPart = WorkspacePart) { workspaceKernel ->
          withCoroutineScope { serviceScope ->
            val setupCausal = causal(change {
              register(VectorEntity)
            })
            reconnectTest(remoteApiDescriptor<RemoteKernel>(),
                          RemoteKernelImpl(workspaceKernel, serviceScope, instructionSet.decoder())) {
              withTransactor(middleware = FollowerTransactorMiddleware(instructionSet.encoder()),
                             registerEntityTypeOnEntityCreation = true,
                             defaultPart = FrontendPart) { frontendKernel ->
                change {
                  register(VectorEntity)
                }
                withRebaseLoop(remoteKernel = CompletableDeferred(remoteApi),
                               instructionSet = instructionSet,
                               reconnectWhenBroken = false) {
                  clientTransport.connect()
                  serviceTransport.connect()
                  setupCausal.await()

                  val messingJob = launch {
                    launch {
                      while (true) {
                        serviceTransport.connect()
                        randomDelay(100)
                        serviceTransport.disconnect()
                        randomDelay(50)
                      }
                    }

                    launch {
                      while (true) {
                        clientTransport.connect()
                        randomDelay(100)
                        clientTransport.disconnect()
                        randomDelay(50)
                      }
                    }
                  }

                  // transact on a connection which is being broken all the time:
                  withTimeoutOrNull(3.seconds) {
                    while (true) {
                      change {
                        val uid = UID.random()
                        shared {
                          addUid(uid)
                        }
                      }
                      delay(5)
                    }
                  }
                  messingJob.cancelAndJoin()

                  val workspaceFinalUid = UID.fromString("WS-FINAL")
                  val frontendFinalUid = UID.fromString("FE-FINAL")
                  clientTransport.connect()
                  serviceTransport.connect()

                  withContext(KernelContextElement(workspaceKernel)) {
                    change {
                      shared {
                        addUid(workspaceFinalUid)
                      }
                    }
                  }

                  // wait for the frontend kernel to receive the snapshot:
                  vectorFlow(frontendKernel).first { vector -> vector.contains(workspaceFinalUid) }

                  change {
                    shared {
                      addUid(frontendFinalUid)
                    }
                  }

                  val workspaceVector = vectorFlow(workspaceKernel).first { vector -> vector.last() == frontendFinalUid }
                  val frontendVector = vectorFlow(frontendKernel).first { vector -> vector == workspaceVector }
                  assertEquals(workspaceVector, frontendVector)
                }
              }
            }
          }
        }
      }
    }
  }
}

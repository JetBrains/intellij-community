// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.Entity
import fleet.util.UID

data class WorkspaceClockEntity(override val eid: EID) : Entity {
  companion object : EntityType<WorkspaceClockEntity>(WorkspaceClockEntity::class, ::WorkspaceClockEntity) {
    val clientClock: ClientClock get() = WorkspaceClockEntity.single().clock
    fun tick(origin: UID) {
      val entity = WorkspaceClockEntity.single()
      requireChangeScope {
        entity[ClockAttr] = entity.clock.tick(origin)
      }
    }

    val ClockAttr = requiredTransient<ClientClock>("clock")
  }

  val clock by ClockAttr
}

fun ChangeScope.initWorkspaceClock() {
  if (WorkspaceClockEntity.singleOrNull() == null) {
    register(WorkspaceClockEntity)
    WorkspaceClockEntity.new {
      it[WorkspaceClockEntity.ClockAttr] = ClientClock.initial(UID.random())
    }
  }
}

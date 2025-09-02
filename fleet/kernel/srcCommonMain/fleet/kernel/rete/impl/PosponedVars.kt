// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete.impl

import com.jetbrains.rhizomedb.Change
import fleet.kernel.rete.*
import fleet.kernel.rete.ReteNetwork
import fleet.kernel.timestamp
import kotlinx.coroutines.flow.StateFlow
import fleet.util.PriorityQueue

internal interface PosponedVars {
  fun command(cmd: Rete.Command)
  fun propagateChange(change: Change)
}

internal fun postponedVars(lastKnownDb: StateFlow<ReteState>, reteNetwork: ReteNetwork): PosponedVars {
  val subs = HashMap<Rete.ObserverId, Subscription>()
  val postponedVars = PriorityQueue<Rete.Command.AddObserver<*>>(compareBy { x -> x.dbTimestamp })
  fun <T> addTerminal(cmd: Rete.Command.AddObserver<T>) {
    val observerId = cmd.observerId
    val observer = cmd.observer
    val subscription = reteNetwork.observeQuery(query = cmd.query,
                                                tracingKey = cmd.tracingKey,
                                                dependencies = cmd.dependencies) { init ->
      onDispose { subs.remove(observerId) }
      observer.run { init(init) }
    }
    subs[observerId] = subscription
  }
  return object : PosponedVars {
    override fun command(cmd: Rete.Command) {
      when (cmd) {
        is Rete.Command.AddObserver<*> -> {
          val db = lastKnownDb.value.dbOrThrow()
          when {
            db.timestamp < cmd.dbTimestamp -> {
              postponedVars.add(cmd)
            }
            else -> {
              addTerminal(cmd)
            }
          }
        }
        is Rete.Command.RemoveObserver -> {
          if (!postponedVars.removeIf { x -> x.observerId == cmd.observerId }) {
            subs.remove(cmd.observerId)?.close()
          }
        }
      }
    }

    override fun propagateChange(change: Change) {
      reteNetwork.propagateChange(change)
      val dbTimestamp = change.dbAfter.timestamp
      tailrec fun loop() {
        val nextTs = postponedVars.peek()?.dbTimestamp
        if (nextTs != null && nextTs <= dbTimestamp) {
          val cmd = postponedVars.poll()!!
          addTerminal(cmd)
          loop()
        }
      }
      loop()
    }
  }
}

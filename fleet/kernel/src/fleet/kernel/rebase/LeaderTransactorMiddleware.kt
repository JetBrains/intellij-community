// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rebase

import com.jetbrains.rhizomedb.*
import fleet.kernel.*
import fleet.util.UID

class LeaderTransactorMiddleware(
  private val instructionEncoder: InstructionEncoder
) : TransactorMiddleware {

  override fun ChangeScope.performChange(next: ChangeScope.() -> Unit) {
    run {
      val sharedChangeScope = SharedChangeScope(this)
      meta[Shared] = object : Shared {
        override fun <T> shared(f: SharedChangeScope.() -> T): T =
          context.alter(
            context.impl
              .withDefaultPart(SharedPart)
              .preventReadsFromLocal()
          ) {
            sharedChangeScope.f()
          }
      }
    }

    val sharedInstructions = ArrayList<SharedInstruction>()
    context.alter(
      context.impl
        .instructionsRecording(
          serContext = InstructionEncodingContext(
            uidAttribute = uidAttribute(),
            encoder = instructionEncoder
          )
        ) { instructionsPair ->
          instructionsPair.sharedEffects.forEach { effect ->
            effect.effect(context)
          }
          if (instructionsPair.sharedInstruction != null &&
              instructionsPair.sharedNovelty.isNotEmpty()) {
            sharedInstructions.add(instructionsPair.sharedInstruction)
          }
        }
        .preventRefsFromShared()
    ) {
      next()
    }

    if (sharedInstructions.isNotEmpty()) {
      val workspaceHand = WorkspaceClockEntity.clientClock
      val transaction = Transaction(
        instructions = sharedInstructions,
        origin = workspaceHand.clientId,
        id = UID.random(),
        index = workspaceHand.index()
      )
      WorkspaceClockEntity.tick(workspaceHand.clientId)
      meta[TransactionResultKey] = TransactionResult.TransactionApplied(transaction)
    }
  }
}

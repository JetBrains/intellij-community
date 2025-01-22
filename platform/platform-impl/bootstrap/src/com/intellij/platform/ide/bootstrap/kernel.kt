// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.kernel.util.kernelCoroutineContext
import com.jetbrains.rhizomedb.EffectInstruction
import com.jetbrains.rhizomedb.MapAttribute
import com.jetbrains.rhizomedb.ReifyEntities
import fleet.kernel.DbSource
import fleet.kernel.TransactorMiddleware
import fleet.kernel.rebase.*
import fleet.kernel.rete.Rete
import fleet.kernel.rete.withRete
import fleet.kernel.transactor
import fleet.kernel.withTransactor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

private val CommonInstructionSet: InstructionSet =
  InstructionSet(listOf(
    AddCoder,
    CompositeCoder,
    RemoveCoder,
    RetractAttributeCoder,
    RetractEntityCoder,
    LocalInstructionCoder(EffectInstruction::class),
    LocalInstructionCoder(MapAttribute::class),
    LocalInstructionCoder(ReifyEntities::class),
    ValidateCoder,
    CreateEntityCoder,
  ))

internal suspend fun startClientKernel(scope: CoroutineScope): KernelStarted {
  return startKernel(scope, FollowerTransactorMiddleware(CommonInstructionSet.encoder()))
}

@ApiStatus.Internal
suspend fun startServerKernel(scope: CoroutineScope): KernelStarted {
  return startKernel(scope, LeaderTransactorMiddleware(CommonInstructionSet.encoder()))
}

private suspend fun startKernel(scope: CoroutineScope, middleware: TransactorMiddleware): KernelStarted {
  return span("Starting kernel") {
    val kernelContextDeferred = CompletableDeferred<CoroutineContext>()
    scope.launch {
      withTransactor(emptyList(), middleware = middleware) { _ ->
        withRete {
          kernelContextDeferred.complete(this.coroutineContext.kernelCoroutineContext())
          awaitCancellation()
        }
      }
    }
    KernelStarted(coroutineContext = kernelContextDeferred.await())
  }
}

@ApiStatus.Internal
data class KernelStarted(val coroutineContext: CoroutineContext)
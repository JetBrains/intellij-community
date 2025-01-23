// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap.kernel

import com.intellij.platform.kernel.util.kernelCoroutineContext
import com.jetbrains.rhizomedb.EffectInstruction
import com.jetbrains.rhizomedb.MapAttribute
import com.jetbrains.rhizomedb.ReifyEntities
import fleet.kernel.TransactorMiddleware
import fleet.kernel.rebase.AddCoder
import fleet.kernel.rebase.CompositeCoder
import fleet.kernel.rebase.CreateEntityCoder
import fleet.kernel.rebase.FollowerTransactorMiddleware
import fleet.kernel.rebase.InstructionSet
import fleet.kernel.rebase.LeaderTransactorMiddleware
import fleet.kernel.rebase.LocalInstructionCoder
import fleet.kernel.rebase.RemoveCoder
import fleet.kernel.rebase.RetractAttributeCoder
import fleet.kernel.rebase.RetractEntityCoder
import fleet.kernel.rebase.ValidateCoder
import fleet.kernel.rete.withRete
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

@ApiStatus.Internal
suspend fun startClientKernel(scope: CoroutineScope): KernelStarted {
  return startKernel(scope, FollowerTransactorMiddleware(CommonInstructionSet.encoder()))
}

@ApiStatus.Internal
suspend fun startServerKernel(scope: CoroutineScope): KernelStarted {
  return startKernel(scope, LeaderTransactorMiddleware(CommonInstructionSet.encoder()))
}

private suspend fun startKernel(scope: CoroutineScope, middleware: TransactorMiddleware): KernelStarted {
  val kernelContextDeferred = CompletableDeferred<CoroutineContext>()
  scope.launch {
    withTransactor(emptyList(), middleware = middleware) { _ ->
      withRete {
        kernelContextDeferred.complete(this.coroutineContext.kernelCoroutineContext())
        awaitCancellation()
      }
    }
  }
  return KernelStarted(coroutineContext = kernelContextDeferred.await())
}

@ApiStatus.Internal
data class KernelStarted(val coroutineContext: CoroutineContext)
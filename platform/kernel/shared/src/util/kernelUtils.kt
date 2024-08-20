// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.util

import com.intellij.ide.plugins.PluginUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.impl.collectEntityClasses
import fleet.kernel.DbSource
import fleet.kernel.Kernel
import fleet.kernel.KernelMiddleware
import fleet.kernel.kernel
import fleet.kernel.rebase.*
import fleet.kernel.rete.Rete
import fleet.kernel.rete.withRete
import fleet.rpc.core.Serialization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.serialization.modules.SerializersModule
import kotlin.coroutines.CoroutineContext

suspend fun <T> withKernel(middleware: KernelMiddleware, body: suspend CoroutineScope.() -> T) {
  val entityClasses = listOf(Kernel::class.java.classLoader).flatMap {
    collectEntityClasses(it, PluginUtil.getPluginId(it).idString)
  }
  fleet.kernel.withKernel(entityClasses, middleware = middleware) { currentKernel ->
    withRete {
      body()
    }
  }
}

fun CoroutineContext.kernelCoroutineContext(): CoroutineContext {
  return kernel + this[Rete]!! + this[DbSource.ContextElement]!!
}

val CommonInstructionSet: InstructionSet =
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

val KernelRpcSerialization = Serialization(lazyOf(SerializersModule {
  registerCRUDInstructions()
}))

suspend fun updateDbInTheEventDispatchThread(): Nothing {
  withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
    try {
      kernel().log.collect { event ->
        DbContext.threadLocal.set(DbContext<DB>(event.db, null))
      }
      awaitCancellation()
    }
    finally {
      DbContext.clearThreadBoundDbContext()
    }
  }
}

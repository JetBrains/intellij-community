// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.util

import com.intellij.ide.plugins.PluginUtil
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.impl.collectEntityClasses
import fleet.kernel.*
import fleet.kernel.rebase.*
import fleet.kernel.rete.Rete
import fleet.kernel.rete.withRete
import fleet.rpc.core.Serialization
import fleet.util.async.conflateReduce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
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

object ReadTracker {
  private val readTrackingIndex = ReadTrackingIndex()
  suspend fun subscribeForChanges() {
    kernel().subscribe(Channel.UNLIMITED) { initial, changes ->
      changes
        .consumeAsFlow()
        .map { c ->
          c.dbAfter to c.novelty
        }
        .conflateReduce { (_, novelty1), (db2, novelty2) ->
          db2 to novelty1 + novelty2
        }
        .collect { (db, novelty) ->
          try {
            withContext(Dispatchers.Main + ModalityState.any().asContextElement()) {
              val lambdas = readTrackingIndex.query(novelty)
              asOf(db.withReadTrackingContext(readTrackingIndex)) {
                lambdas.forEach {
                  readTrackingIndex.runLambda(it)
                }
              }
              DbContext.threadLocal.set(DbContext<DB>(db, null))
            }
          }
          catch (e: Throwable) {
          }
        }
    }
  }
}

val KernelRpcSerialization = Serialization(lazyOf(SerializersModule {
  registerCRUDInstructions()
}))

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.util

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.impl.collectEntityClasses
import fleet.kernel.Kernel
import fleet.kernel.KernelMiddleware
import fleet.kernel.kernel
import fleet.kernel.rebase.*
import fleet.kernel.rete.withRete
import fleet.kernel.subscribe
import fleet.rpc.core.Serialization
import fleet.util.async.conflateReduce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.modules.SerializersModule
import java.util.concurrent.atomic.AtomicInteger

suspend fun <T> withKernel(middleware: KernelMiddleware, body: suspend () -> T) {
  val entityClasses = listOf(Kernel::class.java.classLoader).flatMap(::collectEntityClasses)
  fleet.kernel.withKernel(entityClasses, middleware = middleware) { currentKernel ->
    withRete {
      body()
    }
  }
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
  private val lambdaCounter = AtomicInteger()
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
              DbContext.set(db)
            }
          }
          catch (e: Throwable) {
          }
        }
    }
  }

  @RequiresEdt
  fun forget(id: Int) {
    readTrackingIndex.forget(id)
  }

  /**
   * returns id of registered lambda, it should be passed to forget on disposing
   */
  @RequiresEdt
  fun reactive(f: () -> Unit): Int {
    val id = lambdaCounter.incrementAndGet()
    val lambdaInfo = ReadTrackingIndex.LambdaInfo(id, f)
    asOf(DbContext.threadBound.impl.withReadTrackingContext(readTrackingIndex)) {
      readTrackingIndex.runLambda(lambdaInfo)
    }
    return id;
  }
}

val KernelRpcSerialization = Serialization(SerializersModule {
  registerCRUDInstructions()
})

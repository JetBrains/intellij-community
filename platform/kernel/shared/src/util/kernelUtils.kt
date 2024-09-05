// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.util

import com.intellij.ide.plugins.PluginUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.platform.kernel.EntityTypeProvider
import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.impl.collectEntityClasses
import fleet.kernel.*
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

suspend fun <T> withKernel(middleware: TransactorMiddleware, body: suspend CoroutineScope.() -> T) {
  val entityClasses = listOf(Transactor::class.java.classLoader).flatMap {
    collectEntityClasses(it, PluginUtil.getPluginId(it).idString)
  }
  withTransactor(entityClasses, middleware = middleware) { _ ->
    withRete {
      body()
    }
  }
}

fun handleEntityTypes(transactor: Transactor, coroutineScope: CoroutineScope) {
  transactor.changeAsync {
    for (extension in EntityTypeProvider.EP_NAME.extensionList) {
      for (entityType in extension.entityTypes()) {
        register(entityType)
      }
    }
  }
  EntityTypeProvider.EP_NAME.addExtensionPointListener(coroutineScope, object : ExtensionPointListener<EntityTypeProvider> {
    override fun extensionAdded(extension: EntityTypeProvider, pluginDescriptor: PluginDescriptor) {
      transactor.changeAsync {
        for (entityType in extension.entityTypes()) {
          register(entityType)
        }
      }
    }

    override fun extensionRemoved(extension: EntityTypeProvider, pluginDescriptor: PluginDescriptor) {
      transactor.changeAsync {
        for (entityType in extension.entityTypes()) {
          entityType.delete()
        }
      }
    }
  })
}

fun CoroutineContext.kernelCoroutineContext(): CoroutineContext {
  return transactor + this[Rete]!! + this[DbSource.ContextElement]!!
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
      transactor().log.collect { event ->
        DbContext.threadLocal.set(DbContext<DB>(event.db, null))
      }
      awaitCancellation()
    }
    finally {
      DbContext.clearThreadBoundDbContext()
    }
  }
}

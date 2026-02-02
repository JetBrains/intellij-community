// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.kernel.util

import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.platform.kernel.EntityTypeProvider
import com.intellij.platform.pasta.common.ChangeDocument
import com.jetbrains.rhizomedb.DbContext
import com.jetbrains.rhizomedb.EffectInstruction
import com.jetbrains.rhizomedb.MapAttribute
import com.jetbrains.rhizomedb.Novelty
import com.jetbrains.rhizomedb.ReifyEntities
import fleet.kernel.DbSource
import fleet.kernel.Transactor
import fleet.kernel.TransactorMiddleware
import fleet.kernel.rebase.AddCoder
import fleet.kernel.rebase.CompositeCoder
import fleet.kernel.rebase.CreateEntityCoder
import fleet.kernel.rebase.InstructionSet
import fleet.kernel.rebase.LocalInstructionCoder
import fleet.kernel.rebase.RemoveCoder
import fleet.kernel.rebase.RetractAttributeCoder
import fleet.kernel.rebase.RetractEntityCoder
import fleet.kernel.rebase.ValidateCoder
import fleet.kernel.rete.Rete
import fleet.kernel.rete.withRete
import fleet.kernel.transactor
import fleet.kernel.withTransactor
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext

suspend fun <T> withKernel(middleware: TransactorMiddleware, body: suspend CoroutineScope.() -> T) {
  withTransactor(middleware = middleware) { _ ->
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

/**
 * The latest change is indefinitely stored in [Novelty] with all changed entities.
 * For example, if an entity was removed it and all its fields
 * will be present in [Novelty.retractions] until a new change happens.
 *
 * This might lead to some objects not being collected by GC until the last change is replaced by a new one.
 *
 * This method invokes an empty change on DB to push the last one out of memory.
 * Don't use this method unless there are problems with objects stuck in [Novelty]
 */
@ApiStatus.Internal
suspend fun Transactor.flushLatestChange() {
  this.changeSuspend {  }
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
    ChangeDocument,
  ))

/**
 * Sets the initial value of [DbContext] into [DbContext.threadLocal] on EDT.
 * It should be enough to set it only once, considering that the provided context contains valid [DbContext.dbSource].
 * After that the db snapshot is going to be updated to the latest value on coroutine's continuation by [DbSource.ContextElement]
 * See the explanation in [fleet.kernel.DbSource.ContextElement.restoreThreadContext]
 */
fun updateDbInTheEventDispatchThread(dbContext: DbContext<*>) {
  SwingUtilities.invokeLater {
    dbContext.updateToLatest()
    DbContext.threadLocal.set(dbContext)
  }
}

/**
 * Updates provided [DbContext] to the latest snapshot.
 * Copy-pasted from [fleet.kernel.DbSource.ContextElement.restoreThreadContext]
 */
private fun DbContext<*>.updateToLatest() {
  val dbSource = (dbSource as DbSource?) ?: error("Can get the latest snapshot, DbSource is not set for $this")
  runCatching { dbSource.latest }
    .onSuccess { latest -> set(latest) }
    .onFailure { ex -> setPoison(RuntimeException("Failed to obtain latest db snapshot", ex)) }
}
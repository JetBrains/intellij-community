// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.impl.backend

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.rpc.ProcessHandlerId
import com.intellij.platform.kernel.EntityTypeProvider
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.EntityType
import com.jetbrains.rhizomedb.Indexing

private class ExecutionEntityTypesProvider : EntityTypeProvider {
  override fun entityTypes(): List<EntityType<*>> {
    return listOf(
      BackendProcessHandlerEntity
    )
  }
}


internal data class BackendProcessHandlerEntity(override val eid: EID) : Entity {
  val processHandlerId: ProcessHandlerId by ProcessHandlerId
  val processHandler: ProcessHandler by ProcessHandler

  companion object : EntityType<BackendProcessHandlerEntity>(
    BackendProcessHandlerEntity::class.java.name,
    "com.intellij.xdebugger.impl.rhizome",
    ::BackendProcessHandlerEntity
  ) {
    val ProcessHandlerId: Required<ProcessHandlerId> = requiredTransient("id", Indexing.UNIQUE)
    val ProcessHandler: Required<ProcessHandler> = requiredTransient("handler")
  }
}
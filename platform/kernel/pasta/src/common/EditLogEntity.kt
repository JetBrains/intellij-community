// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pasta.common

import andel.operation.EditLog
import com.jetbrains.rhizomedb.ChangeScope
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import fleet.kernel.Durable
import fleet.kernel.DurableEntityType
import fleet.kernel.StorageKey
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
data class EditLogEntity(override val eid: EID) : Entity {
  val editLog: EditLog by EditLogAttr

  companion object : DurableEntityType<EditLogEntity>(
    EditLogEntity::class.java.name,
    "com.intellij.platform.editor",
    ::EditLogEntity,
  ) {
    val EditLogAttr: Required<EditLog> = requiredValue("editLog", EditLog.serializer())
  }
}

@Experimental
fun ChangeScope.createEmptyEditLog(storageKey: StorageKey? = null): EditLogEntity {
  return EditLogEntity.new {
    it[EditLogEntity.EditLogAttr] = EditLog.empty()
    it[Durable.StorageKeyAttr] = storageKey
  }
}

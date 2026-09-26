// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.ChangeScope
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.EntityType
import com.jetbrains.rhizomedb.RefFlags

private data class RetractionRelation(override val eid: EID) : Entity {
  companion object : EntityType<RetractionRelation>(RetractionRelation::class, ::RetractionRelation) {
    val ParentAttr = requiredRef<Entity>("parent", RefFlags.CASCADE_DELETE_BY)
    val ChildAttr = optionalRef<Entity>("child", RefFlags.CASCADE_DELETE)
  }
}

context(_: ChangeScope)
fun cascadeDelete(parent: Entity, child: Entity?) {
  if (child != null) {
    RetractionRelation.new {
      it[RetractionRelation.ParentAttr] = parent
      it[RetractionRelation.ChildAttr] = child
    }
  }
}

internal fun ChangeScope.registerRetractionRelations() {
  register(RetractionRelation)
}
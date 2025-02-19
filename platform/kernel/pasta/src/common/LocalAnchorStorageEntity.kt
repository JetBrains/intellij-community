// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pasta.common

import andel.editor.AnchorId
import andel.editor.DocumentComponentKey
import andel.editor.RangeMarkerId
import andel.intervals.AnchorStorage
import andel.operation.Operation
import andel.operation.Sticky
import andel.text.Text
import com.jetbrains.rhizomedb.ChangeScope
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.EntityType
import com.jetbrains.rhizomedb.entities
import fleet.util.singleOrNullOrThrow
import org.jetbrains.annotations.ApiStatus.Experimental

private object LocalAnchorStorage: DocumentComponentKey<AnchorStorageComponent>

@Experimental
internal data class LocalAnchorStorageEntity(override val eid: EID) : DocumentComponentEntity<AnchorStorageComponent> {
  val anchorStorage: AnchorStorage by AnchorStorageAttr

  companion object : EntityType<LocalAnchorStorageEntity>(
    LocalAnchorStorageEntity::class.java.name,
    "com.intellij.platform.editor",
    ::LocalAnchorStorageEntity,
    DocumentComponentEntity,
  ) {
    val AnchorStorageAttr: Required<AnchorStorage> = requiredValue("anchorStorage", AnchorStorage.serializer())
  }

  override fun getKey(): DocumentComponentKey<AnchorStorageComponent> = LocalAnchorStorage

  override fun asComponent(changeScope: ChangeScope): AnchorStorageComponent {
    val componentEntity = this
    return object: AnchorStorageComponent {
      override fun edit(before: Text, after: Text, edit: Operation) {
        changeScope.run { componentEntity[AnchorStorageAttr] = anchorStorage.edit(before, after, edit) }
      }
      override fun addAnchor(anchorId: AnchorId, offset: Long, sticky: Sticky) {
        changeScope.run { componentEntity[AnchorStorageAttr] = anchorStorage.addAnchor(anchorId, offset, sticky) }
      }
      override fun addRangeMarker(markerId: RangeMarkerId, from: Long, to: Long, closedLeft: Boolean, closedRight: Boolean) {
        changeScope.run { componentEntity[AnchorStorageAttr] = anchorStorage.addRangeMarker(markerId, from, to, closedLeft, closedRight) }
      }
      override fun removeAnchor(anchorId: AnchorId) {
        changeScope.run { componentEntity[AnchorStorageAttr] = anchorStorage.removeAnchor(anchorId) }
      }
      override fun removeRangeMarker(rangeMarkerId: RangeMarkerId) {
        changeScope.run { componentEntity[AnchorStorageAttr] = anchorStorage.removeRangeMarker(rangeMarkerId) }
      }
    }
  }
}

@Experimental
internal fun ChangeScope.ensureLocalAnchorStorageCreated(document: DocumentEntity): LocalAnchorStorageEntity {
  return entities(DocumentComponentEntity.DocumentAttr, document)
    .filterIsInstance<LocalAnchorStorageEntity>()
    .singleOrNullOrThrow() ?: LocalAnchorStorageEntity.new {
      it[DocumentComponentEntity.DocumentAttr] = document
      it[LocalAnchorStorageEntity.AnchorStorageAttr] = AnchorStorage.empty()
    }
}

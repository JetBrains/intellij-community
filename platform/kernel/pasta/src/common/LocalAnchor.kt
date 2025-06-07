// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pasta.common

import andel.editor.AnchorId
import andel.operation.Sticky
import com.jetbrains.rhizomedb.*
import fleet.util.UID
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
internal data class LocalAnchor(override val eid: EID) : RetractableEntity, Entity {
  val anchorId: AnchorId by AnchorIdAttr
  val anchorStorage: LocalAnchorStorageEntity by AnchorStorageAttr

  companion object : EntityType<LocalAnchor>(
    LocalAnchor::class.java.name,
    "com.intellij.platform.editor",
    ::LocalAnchor,
  ) {
    val AnchorIdAttr: Required<AnchorId> = requiredValue("anchorId", AnchorId.serializer(), Indexing.UNIQUE)
    val AnchorStorageAttr: Required<LocalAnchorStorageEntity> = requiredRef<LocalAnchorStorageEntity>("anchorStorage", RefFlags.CASCADE_DELETE_BY)
  }

  val offset: Long
    get() = requireNotNull(anchorStorage.anchorStorage.resolveAnchor(anchorId))

  val line: Int
    get() = document.text.view().lineAt(offset.toInt()).line

  val document: DocumentEntity
    get() = anchorStorage.document

  override fun onRetract(): RetractableEntity.Callback {
    val anchorStorage = anchorStorage
    val anchorId = anchorId
    return RetractableEntity.Callback {
      if (anchorStorage.exists()) {
        anchorStorage[LocalAnchorStorageEntity.Companion.AnchorStorageAttr] = anchorStorage.anchorStorage.removeAnchor(anchorId)
      }
    }
  }
}

@Experimental
internal fun ChangeScope.createAnchor(
  document: DocumentEntity,
  offset: Long,
  anchorId: AnchorId = AnchorId(UID.random()),
  sticky: Sticky = Sticky.LEFT,
): LocalAnchor {
  require(offset >= 0) { "Offset for the anchor should be >= 0" }
  val storage = ensureLocalAnchorStorageCreated(document)
  storage[LocalAnchorStorageEntity.Companion.AnchorStorageAttr] = storage.anchorStorage
    .removeAnchor(anchorId)
    .addAnchor(anchorId, offset, sticky)
  return entity(LocalAnchor.AnchorIdAttr, anchorId) ?: LocalAnchor.new {
    it[LocalAnchor.AnchorStorageAttr] = storage
    it[LocalAnchor.AnchorIdAttr] = anchorId
  }
}

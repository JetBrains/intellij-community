// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pasta.common

import andel.editor.RangeMarkerId
import andel.editor.substring
import andel.text.TextRange
import com.jetbrains.rhizomedb.*
import fleet.util.UID
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
internal data class LocalRangeMarker(override val eid: EID) : RetractableEntity, Entity {
  val rangeMarkerId: RangeMarkerId by RangeMarkerIdAttr
  val anchorStorage: LocalAnchorStorageEntity by AnchorStorageAttr

  val range: TextRange
    get() = requireNotNull(anchorStorage.anchorStorage.resolveRangeMarker(rangeMarkerId))

  val substring: String
    get() = document.text.substring(range)

  val document: DocumentEntity
    get() = anchorStorage.document

  override fun onRetract(): RetractableEntity.Callback {
    val anchorStorage = anchorStorage
    val rangeMarkerId = rangeMarkerId
    return RetractableEntity.Callback {
      if (anchorStorage.exists()) {
        anchorStorage[LocalAnchorStorageEntity.AnchorStorageAttr] = anchorStorage.anchorStorage.removeRangeMarker(rangeMarkerId)
      }
    }
  }

  companion object : EntityType<LocalRangeMarker>(
    LocalRangeMarker::class.java.name,
    "com.intellij.platform.editor",
    ::LocalRangeMarker,
  ) {
    val RangeMarkerIdAttr: Required<RangeMarkerId> = requiredValue("rangeMarkerId", RangeMarkerId.serializer(), Indexing.UNIQUE)
    val AnchorStorageAttr: Required<LocalAnchorStorageEntity> = requiredRef<LocalAnchorStorageEntity>("anchorStorage", RefFlags.CASCADE_DELETE_BY)
  }
}

@Experimental
internal fun ChangeScope.createRangeMarker(
  document: DocumentEntity,
  from: Long,
  to: Long,
  closedLeft: Boolean = false,
  closedRight: Boolean = false,
  rangeMarkerId: RangeMarkerId = RangeMarkerId(UID.random()),
): LocalRangeMarker {
  require(from >= 0) { "From for the range marker should be >= 0" }
  require(to >= 0) { "To for the range marker should be >= 0" }
  val storage = ensureLocalAnchorStorageCreated(document)
  storage[LocalAnchorStorageEntity.AnchorStorageAttr] = storage.anchorStorage
    .removeRangeMarker(rangeMarkerId)
    .addRangeMarker(rangeMarkerId, from, to, closedLeft, closedRight)
  return entity(LocalRangeMarker.RangeMarkerIdAttr, rangeMarkerId) ?: LocalRangeMarker.new {
    it[LocalRangeMarker.AnchorStorageAttr] = storage
    it[LocalRangeMarker.RangeMarkerIdAttr] = rangeMarkerId
  }
}

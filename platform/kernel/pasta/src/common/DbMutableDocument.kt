// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pasta.common

import andel.editor.*
import andel.intervals.AnchorStorage
import andel.operation.*
import andel.text.LineEnding
import andel.text.Text
import andel.text.TextRange
import com.jetbrains.rhizomedb.ChangeScope
import com.jetbrains.rhizomedb.entities
import com.jetbrains.rhizomedb.entity
import fleet.util.UID
import fleet.util.openmap.BoundedOpenMap
import fleet.util.openmap.MutableBoundedOpenMap
import fleet.util.openmap.MutableOpenMap
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
internal class DbMutableDocument(
  val dbDocument: DocumentEntity,
  private val changeScope: ChangeScope,
  initialMeta: MutableOpenMap<DocumentMeta> = MutableBoundedOpenMap.empty(),
) : MutableDocument {
  private var intermediateAnchorStorage: AnchorStorage = AnchorStorage.empty()

  override val meta: MutableOpenMap<DocumentMeta> = initialMeta
  override val text: Text get() = dbDocument.text
  override val timestamp: Long get() = dbDocument.timestamp
  override val edits: EditLog get() = dbDocument.editLog

  override fun resolveAnchor(anchorId: AnchorId): Long? {
    return intermediateAnchorStorage.resolveAnchor(anchorId) ?: dbDocument.resolveAnchor(anchorId)
  }

  override fun resolveRangeMarker(markerId: RangeMarkerId): TextRange? {
    return intermediateAnchorStorage.resolveRangeMarker(markerId) ?: dbDocument.resolveRangeMarker(markerId)
  }

  override var components: BoundedOpenMap<MutableDocument, DocumentComponent> = BoundedOpenMap.emptyBounded()
    private set
    get() {
      val res = MutableBoundedOpenMap.emptyBounded<MutableDocument, DocumentComponent>()
      val components = entities(DocumentComponentEntity.Companion.DocumentAttr, dbDocument)
      for (c in components) {
        val key = c.getKey()
        res.update(key as DocumentComponentKey<DocumentComponent>) { existing ->
          check(existing == null) {
            "components with the same key=$key: $existing and ${c.asComponent(changeScope)}"
          }
          field[key] ?: c.asComponent(changeScope)
        }
      }
      field = res
      return res
    }

  override fun edit(operation: Operation) {
    if (operation.isIdentity()) return
    require(operation.ops.filterIsInstance<Op.Replace>().none { it.insert.contains(LineEnding.CR.separator) }) {
      "Operation $operation contains LineEnding.CR"
    }
    val textBefore = text
    val versionedOperation = captureOperation(operation)

    changeScope.maybeShared(dbDocument) {
      mutate(
        ChangeDocument(
          operationId = UID.random(),
          documentId = dbDocument.eid,
          operation = versionedOperation.rebase(this@DbMutableDocument)),
      )
    }
    val textAfter = text
    intermediateAnchorStorage = intermediateAnchorStorage.edit(textBefore, textAfter, operation)
    updateComponents(textBefore, textAfter, operation)
  }

  override fun createAnchor(offset: Long, lifetime: AnchorLifetime, sticky: Sticky): AnchorId {
    return when (lifetime) {
      AnchorLifetime.MUTATION -> AnchorId(UID.random()).also {
        intermediateAnchorStorage = intermediateAnchorStorage.addAnchor(it, offset, sticky = sticky)
      }
      AnchorLifetime.DOCUMENT -> changeScope.createAnchor(dbDocument, offset, sticky = sticky).anchorId
    }
  }

  override fun removeAnchor(anchorId: AnchorId) {
    intermediateAnchorStorage = intermediateAnchorStorage.removeAnchor(anchorId)
    with(changeScope) {
      entity(LocalAnchor.Companion.AnchorIdAttr, anchorId)?.delete()
    }
  }

  override fun createRangeMarker(rangeStart: Long, rangeEnd: Long, lifetime: AnchorLifetime): RangeMarkerId {
    return when (lifetime) {
      AnchorLifetime.MUTATION -> RangeMarkerId(UID.random()).also {
        intermediateAnchorStorage = intermediateAnchorStorage.addRangeMarker(
          markerId = it,
          from = rangeStart,
          to = rangeEnd,
          closedLeft = false,
          closedRight = false,
        )
      }
      AnchorLifetime.DOCUMENT -> changeScope.createRangeMarker(
        dbDocument,
        from = rangeStart,
        to = rangeEnd,
        closedLeft = false,
        closedRight = false,
      ).rangeMarkerId
    }
  }

  override fun removeRangeMarker(markerId: RangeMarkerId) {
    intermediateAnchorStorage = intermediateAnchorStorage.removeRangeMarker(markerId)
    with(changeScope) {
      entity(LocalRangeMarker.Companion.RangeMarkerIdAttr, markerId)?.delete()
    }
  }

  override fun batchUpdateAnchors(
    anchorIds: List<AnchorId>, anchorOffsets: LongArray,
    rangeIds: List<RangeMarkerId>, ranges: List<TextRange>,
  ) {
    with(changeScope) {
      val storage = ensureLocalAnchorStorageCreated(dbDocument)
      storage[LocalAnchorStorageEntity.Companion.AnchorStorageAttr] = storage.anchorStorage.batchUpdate(anchorIds, anchorOffsets, rangeIds, ranges)
      anchorIds.forEach { anchorId ->
        entity(LocalAnchor.Companion.AnchorIdAttr, anchorId) ?: LocalAnchor.Companion.new {
          it[LocalAnchor.Companion.AnchorIdAttr] = anchorId
          it[LocalAnchor.Companion.AnchorStorageAttr] = storage
        }
      }
      rangeIds.forEach { rangeMarkerId ->
        entity(LocalRangeMarker.Companion.RangeMarkerIdAttr, rangeMarkerId) ?: LocalRangeMarker.Companion.new {
          it[LocalRangeMarker.Companion.RangeMarkerIdAttr] = rangeMarkerId
          it[LocalRangeMarker.Companion.AnchorStorageAttr] = storage
        }
      }
    }
  }

  private fun updateComponents(textBefore: Text, textAfter: Text, operation: Operation) {
    for (component in components.asMap().values.toList().sortedBy { it.getOrder() }) {
      component.edit(textBefore, textAfter, operation)
    }
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pasta.common

import andel.editor.*
import andel.intervals.AnchorStorage
import andel.operation.EditLog
import andel.operation.Operation
import andel.operation.isNotIdentity
import andel.text.Text
import andel.text.TextRange
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.rhizomedb.*
import fleet.kernel.Durable
import fleet.kernel.DurableEntityType
import fleet.kernel.deprecatedUid
import fleet.util.UID
import fleet.util.openmap.OpenMap
import fleet.util.singleOrNullOrThrow
import kotlinx.serialization.builtins.serializer
import org.jetbrains.annotations.ApiStatus.Experimental
import andel.editor.Document as AndelDocument


private val logger = logger<DocumentEntity>()

@Experimental
class DocumentEntity(override val eid: EID) : Entity {
  val uid: UID by Durable.Id
  val text: Text by TextAttr
  val writable: Boolean by WritableAttr
  val editLogEntity: EditLogEntity by EditLogAttr
  val sharedAnchorStorage: AnchorStorage by SharedAnchorStorageAttr

  companion object : DurableEntityType<DocumentEntity>(
    DocumentEntity::class.java.name,
    "com.intellij.platform.editor",
    ::DocumentEntity,
  ) {
    val TextAttr: Required<Text> = requiredValue("text", Text.serializer())
    val WritableAttr: Required<Boolean> = requiredValue("writable", Boolean.serializer())
    val EditLogAttr: Required<EditLogEntity> = requiredRef<EditLogEntity>("editLogEntity", RefFlags.UNIQUE)
    val SharedAnchorStorageAttr: Required<AnchorStorage> = requiredValue("sharedAnchorStorage", AnchorStorage.serializer())

    fun fromText(uid: UID, text: CharSequence): DocumentEntity = requireChangeScope {
      DocumentEntity.new {
        it[Durable.Id] = uid
        it[TextAttr] = Text.fromString(text.toString())
        it[WritableAttr] = true
        it[EditLogAttr] = createEmptyEditLog()
        it[SharedAnchorStorageAttr] = AnchorStorage.empty()
      }
    }
  }

  val timestamp: Long
    get() = editLogEntity.editLog.timestamp

  val editLog: EditLog
    get() = editLogEntity.editLog

  val editLogUid: UID
    get() = editLogEntity.deprecatedUid()

  val version: UID?
    get() = editLog.version

  fun <T> mutate(
    changeScope: ChangeScope,
    initialMeta: OpenMap<DocumentMeta>,
    f: MutableDocument.() -> T,
  ): T {
    val mutableDocument = DbMutableDocument(this, changeScope, initialMeta.mutable())
    val result = f(mutableDocument)
    for (component in mutableDocument.components.asMap().values.sortedBy { it.getOrder() }) {
      runCatching {
        component.onCommit()
      }.onFailure {
        logger.error("failed onCommit", it)
      }
    }
    return result
  }

  fun editComponents(
    changeScope: ChangeScope,
    components: Set<DocumentComponentEntity<*>>,
    textBefore: Text,
    textAfter: Text,
    op: Operation,
  ) {
    if (op.isNotIdentity()) {
      val sorted = components
        .map { it.asComponent(changeScope) }
        .sortedBy(DocumentComponent::getOrder)
      sorted.forEach { c -> c.edit(textBefore, textAfter, op) }
      sorted.forEach { c -> c.onCommit() }
    }
  }

  fun resolveAnchor(anchorId: AnchorId): Long? {
    return entities(DocumentComponentEntity.DocumentAttr, this)
      .filterIsInstance<LocalAnchorStorageEntity>()
      .singleOrNullOrThrow()
      ?.anchorStorage
      ?.resolveAnchor(anchorId)
  }

  fun resolveRangeMarker(markerId: RangeMarkerId): TextRange? {
    return entities(DocumentComponentEntity.DocumentAttr, this)
      .filterIsInstance<LocalAnchorStorageEntity>()
      .singleOrNullOrThrow()
      ?.anchorStorage
      ?.resolveRangeMarker(markerId)
  }

  fun asDocument(): AndelDocument {
    val dbDocument = this
    return object : AndelDocument {
      override val text: Text get() = dbDocument.text
      override val timestamp: Long get() = dbDocument.timestamp
      override val edits: EditLog get() = dbDocument.editLog
      override fun resolveAnchor(anchorId: AnchorId): Long? = dbDocument.resolveAnchor(anchorId)
      override fun resolveRangeMarker(markerId: RangeMarkerId): TextRange? = dbDocument.resolveRangeMarker(markerId)
    }
  }
}

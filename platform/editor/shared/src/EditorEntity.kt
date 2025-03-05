// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.ad.AdDocumentEntity
import com.jetbrains.rhizomedb.EID
import com.jetbrains.rhizomedb.Entity
import com.jetbrains.rhizomedb.Indexing
import com.jetbrains.rhizomedb.RefFlags
import fleet.kernel.DurableEntityType
import fleet.util.serialization.DataSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
data class EditorEntity(override val eid: EID) : Entity {

  val document: AdDocumentEntity by documentAttr
  val id: EditorId by idAttr
  val clientId: ClientId by clientIdAttr

  companion object : DurableEntityType<EditorEntity>(
    EditorEntity::class.java.name,
    "com.intellij.platform.editor",
    ::EditorEntity,
  ) {

    val idAttr: Required<EditorId> = requiredValue("id", EditorId.serializer(), Indexing.UNIQUE)
    val documentAttr: Required<AdDocumentEntity> = requiredRef("document", RefFlags.CASCADE_DELETE_BY)
    val clientIdAttr: Required<ClientId> = requiredValue("clientId", ClientIdSerializer)
  }
}

private object ClientIdSerializer : DataSerializer<ClientId, String>(String.serializer()) {
  override fun fromData(data: String): ClientId = ClientId(data)
  override fun toData(value: ClientId): String = value.value
}

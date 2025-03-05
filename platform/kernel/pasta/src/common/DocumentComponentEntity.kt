// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pasta.common

import andel.editor.DocumentComponent
import andel.editor.DocumentComponentKey
import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.get
import org.jetbrains.annotations.ApiStatus.Experimental


@Experimental
internal interface DocumentComponentFactory<T : DocumentComponent> {
  val key: DocumentComponentKey<T>
  fun ChangeScope.asComponent(): T
}

@Experimental
interface DocumentComponentEntity<T : DocumentComponent> : Entity {
  val document: DocumentEntity
    get() = this[DocumentAttr]

  companion object : Mixin<DocumentComponentEntity<*>>(DocumentComponentEntity::class) {
    val DocumentAttr: Required<DocumentEntity> = requiredRef<DocumentEntity>("document", RefFlags.CASCADE_DELETE_BY)
  }

  fun asComponent(changeScope: ChangeScope): T

  fun getKey(): DocumentComponentKey<T> = Key(eid)

  private data class Key<T : DocumentComponent>(val eid: EID) : DocumentComponentKey<T>
}

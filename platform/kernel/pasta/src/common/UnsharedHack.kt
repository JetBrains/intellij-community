// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pasta.common

import com.jetbrains.rhizomedb.ChangeScope
import com.jetbrains.rhizomedb.Entity
import fleet.kernel.*
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
internal fun <T> ChangeScope.maybeShared(entity: Entity, f: SharedChangeScope.() -> T): T {
  return if (entity.isShared) shared(f)
  else unshared(f)
}

@Experimental
internal fun <T> ChangeScope.maybeSharedRead(doc: DocumentEntity, f: SharedChangeScope.() -> T): ReplayingValue<T> {
  val box = ReplayingValue<T>()
  maybeShared(doc) {
    box.value = f()
  }
  return box
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import org.jetbrains.annotations.ApiStatus
import java.net.URI

/** [id] must be unique across all other instances registered in [IjentSessionRegistry]. */
@ApiStatus.Experimental
data class IjentId(val id: String) {
  init {
    try {
      check(URI("ijent", id, null, null, null).authority == id)
    }
    catch (err: Exception) {
      throw IllegalArgumentException("IjentId must be URI-serializable, but this value isn't: $id", err)
    }
  }

  override fun toString(): String = "IjentId($id)"
}
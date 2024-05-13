// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import java.net.URI

/** [id] must be unique across all other instances registered in [IjentSessionRegistry]. */
data class IjentId(val id: String) {
  val uri = URI("ijent", null, id, -1, null, null, null)

  init {
    try {
      check(uri.authority == id)
    }
    catch (err: Exception) {
      throw IllegalArgumentException("IjentId must be URI-serializable, but this value isn't: $id", err)
    }
  }

  override fun toString(): String = "IjentId($id)"
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser.elements

import com.intellij.openapi.client.ClientKind as CK

enum class ClientKind {
  LOCAL, FRONTEND, CONTROLLER, GUEST, OWNER, REMOTE, ALL;

  companion object {
    fun ClientKind.convert(): CK = when (this) {
      LOCAL -> CK.LOCAL
      FRONTEND -> CK.FRONTEND
      CONTROLLER -> CK.CONTROLLER
      GUEST -> CK.GUEST
      OWNER -> CK.OWNER
      REMOTE -> CK.REMOTE
      ALL -> CK.ALL
      else -> throw IllegalArgumentException("Unknown client kind: $this")
    }
  }
}
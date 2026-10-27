// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class URIUtilTest {
  @Test
  fun `encodePath keeps regular paths`() {
    assertEquals(
      "src/main.kt",
      URIUtil.encodePath("src/main.kt"),
    )
  }

  @Test
  fun `encodePath encodes special characters in nested path segments`() {
    assertEquals(
      "src/directory%20name/file%2Bname%26value%23name%3F.kt",
      URIUtil.encodePath("src/directory name/file+name&value#name?.kt"),
    )
  }

  @Test
  fun `encodePath encodes unicode and literal encoded text`() {
    assertEquals(
      "src/caf%C3%A9/%252B.kt",
      URIUtil.encodePath("src/caf\u00e9/%2B.kt"),
    )
  }

  @Test
  fun `encodePath encodes percent signs once`() {
    assertEquals(
      "src/100%25/file.kt",
      URIUtil.encodePath("src/100%/file.kt"),
    )
  }
}

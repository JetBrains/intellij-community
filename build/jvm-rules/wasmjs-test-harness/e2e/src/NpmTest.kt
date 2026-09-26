// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package e2e

import e2e.nanoid.nanoid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NpmTest {
  @Test
  fun importsNpmPackageThroughTheImportMap() {
    assertEquals(21, nanoid().length)
    assertNotEquals(nanoid(), nanoid())
  }
}

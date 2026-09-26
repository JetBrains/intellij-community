// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package e2e

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertTrue

// Uses real kotlinx-datetime API so the *compiler* generates the internal `@js-joda/core`
// import; the runtime only works because the kotlinx-datetime wasmjs_import declares the
// @js-joda/core npm package, which wasmjs_test propagates into the import map (the test
// itself has no npm_packages entry for it).
class KotlinxDatetimeTest {
  @Test
  fun compilerGeneratedJsJodaImportResolvesThroughPropagatedNpmPackages() {
    assertTrue(TimeZone.currentSystemDefault().id.isNotEmpty())
  }
}

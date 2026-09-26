// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package e2e

import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleRuntimeTest {
  // `./marker.mjs` resolves relative to the linked module, where the file does not exist; the
  // import map remaps it to /_runtime/marker.mjs. This is exactly how skiko's `./skiko.mjs`
  // import is served for fleet's UI test modules.
  @Test
  fun moduleAdjacentImportIsRemappedToRuntimeFiles(): Promise<JsAny?> =
    importMarker().then { module ->
      assertEquals("module-adjacent", module.marker.toString())
      null
    }

  @Test
  fun awaitedImportCompletedBeforeTheEntrypoint() {
    assertEquals(true, markerReady())
  }
}

private fun importMarker(): Promise<MarkerModule> = js("import('./marker.mjs')")

// An external interface, not class: a module namespace object is not an instance of any
// global constructor, and interfaces skip the generated `instanceof` check.
private external interface MarkerModule : JsAny {
  val marker: JsAny
}

private fun markerReady(): Boolean = js("globalThis.__markerReady === true")

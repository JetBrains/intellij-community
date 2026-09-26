// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NpmEntryResolverTest {
  @Test
  fun `module field wins over main`() {
    // The @js-joda/core shape: no exports, module + main.
    assertEquals(
      "dist/js-joda.esm.js",
      resolveNpmEntry("pkg", """{"main": "dist/js-joda.js", "module": "dist/js-joda.esm.js"}"""),
    )
  }

  @Test
  fun `exports string form wins`() {
    assertEquals("lib/index.mjs", resolveNpmEntry("pkg", """{"exports": "./lib/index.mjs", "main": "lib/index.cjs"}"""))
  }

  @Test
  fun `exports root entry with conditions resolves the import condition`() {
    assertEquals(
      "esm/entry.js",
      resolveNpmEntry("pkg", """{"exports": {".": {"import": "./esm/entry.js", "require": "./cjs/entry.js"}}}"""),
    )
  }

  @Test
  fun `exports conditions without subpaths resolve directly`() {
    assertEquals(
      "esm/entry.js",
      resolveNpmEntry("pkg", """{"exports": {"default": "./esm/entry.js"}}"""),
    )
  }

  @Test
  fun `main is the fallback`() {
    assertEquals("lib/main.js", resolveNpmEntry("pkg", """{"main": "./lib/main.js"}"""))
  }

  @Test
  fun `index js is the last resort`() {
    assertEquals("index.js", resolveNpmEntry("pkg", """{"name": "whatever"}"""))
  }

  @Test
  fun `array exports resolve to the first usable entry`() {
    assertEquals("esm/entry.js", resolveNpmEntry("pkg", """{"exports": ["./esm/entry.js", "./fallback.js"]}"""))
  }

  @Test
  fun `nested export conditions resolve recursively`() {
    assertEquals(
      "esm/entry.js",
      resolveNpmEntry("pkg", """{"exports": {".": {"import": {"default": "./esm/entry.js"}}}}"""),
    )
  }

  @Test
  fun `exports with only subpaths and no root entry fail`() {
    val exception = assertThrows(IllegalStateException::class.java) {
      resolveNpmEntry("subpaths-only", """{"exports": {"./sub": "./sub.js"}, "main": "lib/main.js"}""")
    }
    assertTrue(exception.message.orEmpty().contains("subpaths-only"))
  }

  @Test
  fun `a string browser field wins over main but loses to module`() {
    assertEquals("browser/entry.js", resolveNpmEntry("pkg", """{"browser": "./browser/entry.js", "main": "lib/main.cjs"}"""))
    assertEquals(
      "esm/entry.js",
      resolveNpmEntry("pkg", """{"module": "esm/entry.js", "browser": "./browser/entry.js", "main": "lib/main.cjs"}"""),
    )
    // The remap-object form of `browser` is not an entry point; resolution falls through to main.
    assertEquals("lib/main.js", resolveNpmEntry("pkg", """{"browser": {"./x.js": "./y.js"}, "main": "lib/main.js"}"""))
  }

  @Test
  fun `exports without a browser-usable condition fail instead of falling back to main`() {
    val exception = assertThrows(IllegalStateException::class.java) {
      resolveNpmEntry("cjs-only", """{"exports": {".": {"require": "./cjs/entry.js"}}, "main": "cjs/entry.js"}""")
    }
    assertTrue(exception.message.orEmpty().contains("cjs-only"))
  }

  @Test
  fun `failures name the package`() {
    // The harness resolves many packages in one run; the broken one must be identifiable.
    listOf("[1, 2]", "not json at all").forEach { packageJson ->
      val exception = assertThrows(IllegalStateException::class.java) {
        resolveNpmEntry("@scope/broken", packageJson)
      }
      assertTrue(exception.message.orEmpty().contains("@scope/broken"))
    }
  }
}

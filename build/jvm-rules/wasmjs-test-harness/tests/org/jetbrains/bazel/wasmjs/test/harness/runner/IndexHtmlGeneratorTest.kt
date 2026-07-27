// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IndexHtmlGeneratorTest {
  @Test
  fun `a test without configuration scripts, npm packages or awaited imports`() {
    val page = generateIndexHtml(
      entrypointModulePath = "my-module-js/my-module.mjs",
      configurationScriptPaths = emptyList(),
      npmPackageEntries = emptyMap(),
    )

    assertEquals(fixture("minimal-page.html"), page)
  }

  @Test
  fun `configuration scripts, the import map and awaited imports keep their declared order`() {
    val page = generateIndexHtml(
      entrypointModulePath = "my-module-js/my-module.mjs",
      configurationScriptPaths = listOf("_config/first.js", "_config/second.js"),
      npmPackageEntries = mapOf("@js-joda/core" to "dist/js-joda.esm.js"),
      importRemaps = mapOf("my-module-js/skiko.mjs" to "_runtime/skiko.mjs"),
      awaitedImports = listOf("_runtime/skiko.mjs" to "awaitSkiko", "_runtime/second.mjs" to "ready"),
    )

    assertEquals(fixture("full-page.html"), page)
  }

  @Test
  fun `awaited import members must be plain JS identifiers`() {
    assertThrows(IllegalStateException::class.java) {
      generateIndexHtml(
        entrypointModulePath = "m-js/m.mjs",
        configurationScriptPaths = emptyList(),
        npmPackageEntries = emptyMap(),
        awaitedImports = listOf("_runtime/skiko.mjs" to "awaitSkiko; alert(1)"),
      )
    }
  }

  @Test
  fun `unsafe paths are rejected`() {
    listOf(
      """m-js/<script>"evil"</script>.mjs""",
      "m-js/line\nbreak.mjs",
      "m-js/carriage\rreturn.mjs",
    ).forEach { path ->
      assertThrows(path, IllegalStateException::class.java) {
        generateIndexHtml(
          entrypointModulePath = path,
          configurationScriptPaths = emptyList(),
          npmPackageEntries = emptyMap(),
        )
      }
    }
  }

  private fun fixture(name: String): String =
    checkNotNull(javaClass.getResourceAsStream("fixtures/$name")) { "missing fixture: $name" }.use { stream ->
      stream.readBytes().decodeToString()
    }
}

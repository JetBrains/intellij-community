// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Builds the `index.html` of a wasmjs_test: the page that loads the test module in the browser.
 *
 * The page is assembled here instead of resolving a template resource, so everything the browser
 * runs is readable in one place. Only four parts vary: the entrypoint import, one classic
 * `<script src>` tag per configuration script, an import map resolving the bare specifiers of npm
 * packages served under `/node_modules/` plus the [importRemaps] entries (omitted when there is
 * nothing to map), and the [awaitedImports] statements run before the entrypoint (omitted when the
 * test declares none).
 *
 * [importRemaps] redirects module-adjacent imports (e.g. the linked module's `./skiko.mjs`) to
 * where the file is actually served (`/_runtime/skiko.mjs`): a linked module is a single Bazel
 * tree artifact, so extra files cannot sit inside its directory, but import maps remap the
 * resolved URL. [awaitedImports] awaits an exported promise before the entrypoint runs (e.g.
 * skiko's `awaitSkiko`, whose skia bindings are lazy stubs until its wasm finishes loading).
 */
internal fun generateIndexHtml(
  entrypointModulePath: String,
  configurationScriptPaths: List<String>,
  npmPackageEntries: Map<String, String>,
  importRemaps: Map<String, String> = emptyMap(),
  awaitedImports: List<Pair<String, String>> = emptyList(),
): String = buildString {
  appendLine("""<!DOCTYPE html>""")
  appendLine("""<html lang="en">""")
  appendLine("""<head>""")
  appendLine("""  <meta charset="utf-8">""")
  appendLine("""  <title>wasmjs_test</title>""")
  when {
    npmPackageEntries.isEmpty() && importRemaps.isEmpty() -> {}
    else -> {
      val imports = buildJsonObject {
        putJsonObject("imports") {
          npmPackageEntries.forEach { (specifier, entryPath) ->
            // Exact bare specifier to the package entry module, plus the subpath prefix form
            // (`pkg/sub/module.js`) which import maps resolve via trailing-slash keys.
            put(specifier, "/node_modules/$specifier/${entryPath.removePrefix("/")}")
            put("$specifier/", "/node_modules/$specifier/")
          }
          importRemaps.forEach { (from, to) ->
            // Root-relative keys match after URL resolution, so a module-relative import like
            // `./skiko.mjs` from inside the linked module directory is remapped too.
            put("/${validatedPagePath(from)}", "/${validatedPagePath(to)}")
          }
        }
      }
      appendLine("""  <script type="importmap">""")
      appendLine("""    $imports""")
      appendLine("""  </script>""")
    }
  }
  configurationScriptPaths.forEach { path ->
    appendLine("""  <script src="/${validatedPagePath(path)}"></script>""")
  }
  appendLine("""</head>""")
  appendLine("""<body>""")
  appendLine("""<script type="module">""")
  // Bazel's --test_filter is only known at test runtime: the runner forwards it as repeated
  // `include` query parameters, translated here into the argv-style filters kotlin-test reads
  // through this Node-process shim (there is no Node in a browser). kotlin-test's argument
  // parser (kotlin.test FrameworkTestArguments) expects `--include` and the comma-separated
  // patterns as two separate tokens, skips the first two argv entries, and re-splits on spaces.
  appendLine("""  const includes = new URLSearchParams(window.location.search).getAll("include");""")
  appendLine("""  const filterArguments = includes.length === 0 ? [] : ["--include", includes.join(",")];""")
  appendLine("""  globalThis.process = { argv: ["NEVER_PROCESSED", "NEVER_PROCESSED", ...filterArguments], env: {}, release: {} };""")
  appendLine("""  globalThis.addEventListener("error", (event) => {""")
  appendLine("""    console.error(`wasmjs_test page error: ${'$'}{event.message}`);""")
  appendLine("""  });""")
  // The entrypoint runs in an async IIFE, so its failure mode is an unhandled rejection, which
  // the "error" event does not cover (CDP reports it too; this keeps the page-side log complete).
  appendLine("""  globalThis.addEventListener("unhandledrejection", (event) => {""")
  appendLine("""    console.error(`wasmjs_test page error: ${'$'}{event.reason}`);""")
  appendLine("""  });""")
  // A missing export would make `await` succeed immediately (awaiting undefined), silently
  // reintroducing the very race awaited imports exist to prevent — so it fails loudly instead.
  appendLine("""  const awaitedExport = (module, path, member) => {""")
  appendLine("""    if (!(member in module)) {""")
  appendLine("""      throw new Error(`awaited import ${'$'}{path} has no export '${'$'}{member}'`);""")
  appendLine("""    }""")
  appendLine("""    return module[member];""")
  appendLine("""  };""")
  appendLine("""  globalThis.__kotlinTestRun = (async () => {""")
  // Awaited imports run before the entrypoint: they synchronize on module-adjacent runtime
  // the tests use synchronously (e.g. skiko's `awaitSkiko` wasm-readiness promise), which
  // the tests would otherwise race against. Empty when the test declares none.
  awaitedImports.forEach { (path, member) ->
    // awaitedExport is defined by the page; it throws when the export is missing instead of
    // letting `await undefined` silently skip the synchronization.
    val pagePath = "/${validatedPagePath(path)}"
    appendLine("""    await awaitedExport(await import("$pagePath"), "$pagePath", "${validatedJsIdentifier(member)}");""")
  }
  // startUnitTests is only exported when the linked module contains kotlin-test tests, and
  // it returns undefined: async test completion is observed on the console stream, not here.
  appendLine("""    return (await import("./${validatedPagePath(entrypointModulePath)}")).startUnitTests?.();""")
  appendLine("""  })();""")
  appendLine("""</script>""")
  appendLine("""</body>""")
  appendLine("""</html>""")
}

private fun validatedPagePath(path: String): String {
  check(path.none { it == '"' || it == '<' || it == '>' || it == '\n' || it == '\r' }) {
    "path is not safe to embed in index.html: $path"
  }
  return path
}

private val JS_IDENTIFIER = Regex("""[A-Za-z_$][A-Za-z0-9_$]*""")

private fun validatedJsIdentifier(member: String): String {
  check(member.matches(JS_IDENTIFIER)) { "awaited import member is not a plain JS identifier: $member" }
  return member
}

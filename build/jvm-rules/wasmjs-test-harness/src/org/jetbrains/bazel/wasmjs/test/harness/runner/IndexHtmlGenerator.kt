// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Builds the `index.html` of a wasmjs_test: the page that loads the test module in the browser.
 *
 * The page is assembled here instead of resolving a template resource, so everything the browser
 * runs is readable in one place. It follows the `static/test.html` reference page of
 * kotlin-web-helpers: [mochaScriptPath] and [kotlinTestRunnerScriptPath] install mocha, the
 * kotlin-test adapter, and the TeamCity reporter. The page prints [TESTS_FINISHED_MARKER] one
 * macrotask after mocha completes the run.
 *
 * The other parts that vary: the entrypoint import, one classic `<script src>` tag per
 * configuration script, an import map resolving the bare specifiers of npm packages served under
 * `/node_modules/` plus the [importRemaps] entries (omitted when there is nothing to map), and the
 * [awaitedImports] statements run before the entrypoint (omitted when the test declares none).
 *
 * [importRemaps] redirects module-adjacent imports (e.g. the linked module's `./skiko.mjs`) to
 * where the file is actually served (`/_runtime/skiko.mjs`): a linked module is a single Bazel
 * tree artifact, so extra files cannot sit inside its directory, but import maps remap the
 * resolved URL. [awaitedImports] awaits an exported promise before the entrypoint runs (e.g.
 * skiko's `awaitSkiko`, whose skia bindings are lazy stubs until its wasm finishes loading).
 */
internal fun generateIndexHtml(
  entrypointModulePath: String,
  mochaScriptPath: String,
  kotlinTestRunnerScriptPath: String,
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
  appendLine("""<script>""")
  appendLine("""  globalThis.addEventListener("error", (event) => {""")
  appendLine("""    console.error(`wasmjs_test page error: ${'$'}{event.message}`);""")
  appendLine("""  });""")
  // The entrypoint runs in an async IIFE, so its failure mode is an unhandled rejection, which
  // the "error" event does not cover (CDP reports it too; this keeps the page-side log complete).
  appendLine("""  globalThis.addEventListener("unhandledrejection", (event) => {""")
  appendLine("""    console.error(`wasmjs_test page error: ${'$'}{event.reason}`);""")
  appendLine("""  });""")
  appendLine("""</script>""")
  appendLine("""<script src="/${validatedPagePath(mochaScriptPath)}"></script>""")
  // The runner reads this configuration when its script loads, so it must come first.
  appendLine("""<script>""")
  // Bazel's --test_filter and --test_runner_fail_fast are only known at test runtime: the runner
  // forwards them as repeated `include` query parameters and a `failFast` query parameter. The
  // kotlin-test runner expects `--include` and the comma-separated patterns as two separate arguments.
  appendLine("""  const parameters = new URLSearchParams(window.location.search);""")
  appendLine("""  const includes = parameters.getAll("include");""")
  appendLine("""  window.kotlinTestBrowserRunner = {""")
  appendLine("""    kotlinTestCliArguments: includes.length === 0 ? [] : ["--include", includes.join(",")],""")
  // Without a page element for mocha's HTML reporter, the reporter needs the base reporter for its statistics.
  appendLine("""    reporterOptions: { Base: Mocha.reporters.Base },""")
  // Mocha fails a test after 2 seconds by default. The deadline derived from TEST_TIMEOUT bounds the run instead.
  // With `bail`, mocha runs no more tests after the first failure, and still closes the suites it started.
  appendLine("""    mochaSetupOptions: { timeout: 0, bail: parameters.has("failFast") },""")
  // The page prints the marker the harness waits for, not the runner. The extra macrotask lets a
  // callback that a test scheduled with `setTimeout(0)` run first: mocha removes its error hook
  // when the run ends, so an exception in that callback reaches the harness as uncaught.
  appendLine("""    testsFinishedMarker: "$MOCHA_RUN_COMPLETED_LINE",""")
  appendLine("""    onComplete: () => setTimeout(() => console.log("$TESTS_FINISHED_MARKER"), 0),""")
  appendLine("""  };""")
  appendLine("""</script>""")
  appendLine("""<script src="/${validatedPagePath(kotlinTestRunnerScriptPath)}"></script>""")
  appendLine("""<script type="module">""")
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
  // The page imports the entrypoint itself instead of through the runner's `kotlinWasmJsTestsEntry`:
  // the runner logs a failed import and runs zero tests, but here the failure stays an uncaught
  // rejection, which the harness reports as an infrastructure failure.
  // startUnitTests is only exported when the linked module contains kotlin-test tests. It
  // registers the kotlin-test suites with mocha, and run() then executes them.
  appendLine("""    (await import("./${validatedPagePath(entrypointModulePath)}")).startUnitTests?.();""")
  appendLine("""    await window.kotlinTestBrowserRunner.run();""")
  appendLine("""  })();""")
  appendLine("""</script>""")
  appendLine("""</body>""")
  appendLine("""</html>""")
}

/**
 * The console line that ends the run: the page prints it after mocha completes the run. It is
 * the default marker of the kotlin-web-helpers runner.
 */
internal const val TESTS_FINISHED_MARKER = "KOTLIN-TEST-FINISHED"

/** The marker of the kotlin-test runner itself. The harness reports it as output outside a test. */
private const val MOCHA_RUN_COMPLETED_LINE = "wasmjs_test: mocha completed the run"

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

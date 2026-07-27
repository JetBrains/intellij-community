// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Resolves the ES-module entry file of an npm package from its `package.json`, the path an
 * import map must point a bare specifier at.
 *
 * Resolution order follows what bundlers do for a browser ESM consumer: the `exports` map's
 * root entry (preferring the `import`/`module`/`browser`/`default` conditions) — authoritative
 * when present, like Node treats it — then the top-level `module` field, then a string
 * `browser` field, then `main`, then the `index.js` convention.
 */
internal fun resolveNpmEntry(specifier: String, packageJson: String): String {
  //@formatter:off
  val root = packageJsonObject(specifier, packageJson)
  val entry = when (val exports = root["exports"]) {
    null -> (root["module"] as? JsonPrimitive)?.contentOrNull()
            ?: (root["browser"] as? JsonPrimitive)?.contentOrNull()
            ?: (root["main"] as? JsonPrimitive)?.contentOrNull()
            ?: "index.js"
    // No fallback past a present `exports`: falling back to `main` would hand the browser a CJS
    // file that fails later as an opaque import error instead of this resolution-time message.
    else -> resolveExports(exports)
            ?: error("npm package $specifier: `exports` offers no browser-usable root entry (conditions: ${importConditions.joinToString()})")
  }
  //@formatter:on
  return entry.removePrefix("./")
}

/** Failures name the package: the harness resolves many, and the broken one must be identifiable. */
private fun packageJsonObject(specifier: String, packageJson: String): JsonObject {
  val root = try {
    Json.parseToJsonElement(packageJson)
  }
  catch (e: SerializationException) {
    error("npm package $specifier: cannot parse package.json: ${e.message}")
  }
  return root as? JsonObject ?: error("npm package $specifier: package.json is not a JSON object")
}

private val importConditions = listOf("import", "module", "browser", "default")

private fun resolveExports(exports: JsonElement): String? = when (exports) {
  is JsonPrimitive -> exports.contentOrNull()
  is JsonObject -> when {
    exports.keys.any { it.startsWith(".") } -> exports["."]?.let(::resolveExports)
    else -> importConditions.firstNotNullOfOrNull { condition -> exports[condition]?.let(::resolveExports) }
  }
  is JsonArray -> exports.firstNotNullOfOrNull(::resolveExports)
}

private fun JsonPrimitive.contentOrNull(): String? = when {
  isString -> content
  else -> null
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.networknt.schema.Error

/**
 * Returns true if the error's evaluationPath goes through a oneOf or anyOf branch.
 * Used to decide whether required errors should be pre-grouped: branch-specific required errors
 * must NOT be pre-grouped so that higher-priority errors (e.g., type mismatches from another
 * branch) can win the putIfAbsent race in the main error loop.
 */
fun isInsideComposition(error: Error): Boolean {
  val evalPath = error.evaluationPath?.toString() ?: return false
  return "/oneOf/" in evalPath || "/anyOf/" in evalPath
}

/**
 * Returns true if [candidate]'s evaluationPath is a strict descendant of [composition]'s,
 * i.e. the candidate is a branch error under this specific composition. Used to prevent
 * cross-composition sibling leaks when two compositions evaluate at the same instance node
 * (e.g. sibling oneOf + anyOf, or nested if/then + oneOf).
 */
fun isBranchErrorOf(composition: Error, candidate: Error): Boolean {
  val compPath = composition.evaluationPath?.toString()
  val candPath = candidate.evaluationPath?.toString()
  if (compPath.isNullOrEmpty() || candPath.isNullOrEmpty()) return false
  return candPath.startsWith("$compPath/")
}

fun extractCompositionRoot(evaluationPath: String?): String? {
  if (evaluationPath.isNullOrEmpty()) return null
  // Prefer the RIGHTMOST composition segment — we want the innermost composition
  // containing the current error so that sibling-branch detection stays local
  // and nested compositions (oneOf inside an anyOf inside another oneOf) are
  // handled at the level where their branches actually diverge.
  val oneOfIdx = evaluationPath.lastIndexOf("/oneOf/")
  val anyOfIdx = evaluationPath.lastIndexOf("/anyOf/")
  val idx = maxOf(oneOfIdx, anyOfIdx)
  if (idx < 0) return null
  val keywordLen = if (oneOfIdx > anyOfIdx) "/oneOf".length else "/anyOf".length
  return evaluationPath.substring(0, idx + keywordLen)
}

fun extractBranchIndex(evaluationPath: String?, compositionRoot: String?): Int? {
  if (evaluationPath == null || compositionRoot == null) return null
  if (!evaluationPath.startsWith("$compositionRoot/")) return null
  val rest = evaluationPath.substring(compositionRoot.length + 1)
  val slash = rest.indexOf('/')
  val branchSegment = if (slash >= 0) rest.substring(0, slash) else rest
  return branchSegment.toIntOrNull()
}

/**
 * Schema-level redirect keywords that appear in [com.networknt.schema.Error.evaluationPath]
 * between a oneOf/anyOf branch index and a deeper keyword without changing the instance node
 * being validated. Used to recognise `/oneOf/N/$ref/type` and similar as semantically equivalent
 * to `/oneOf/N/type` — both are top-level type failures of branch N.
 */
private val SCHEMA_REDIRECT_SEGMENTS = setOf("\$ref", "\$dynamicRef", "\$recursiveRef")

/**
 * True if [tail] is a path from a oneOf/anyOf branch index down to a final `/type`, consisting
 * only of schema-redirect segments (e.g. `/$ref`). Examples that match: `"/type"`, `"/$ref/type"`,
 * `"/$dynamicRef/type"`. Examples that do not match: `"/items/type"`, `"/properties/x/type"`,
 * `"/oneOf/0/type"` — those navigate into a child instance node, so the trailing `type` failure
 * is NOT a top-level branch failure.
 */
fun isBranchRedirectToType(tail: String): Boolean {
  if (tail == "/type") return true
  if (!tail.endsWith("/type")) return false
  val redirect = tail.removeSuffix("/type").removePrefix("/")
  if (redirect.isEmpty()) return false
  return redirect.split("/").all { it in SCHEMA_REDIRECT_SEGMENTS }
}

/**
 * Returns true if [evaluationPath] points to a top-level `type` failure of a oneOf/anyOf
 * branch — either directly (`<compRoot>/<idx>/type`) or via schema-level redirects
 * (`<compRoot>/<idx>/$ref/type`, etc.). Type errors that navigate into the branch's own
 * children (`/items/type`, `/properties/foo/type`, `/anyOf/0/type`) are NOT top-level.
 */
fun isTopLevelBranchTypeError(evaluationPath: String?): Boolean {
  if (evaluationPath == null) return false
  val compRoot = extractCompositionRoot(evaluationPath) ?: return false
  val branchIdx = extractBranchIndex(evaluationPath, compRoot) ?: return false
  val rest = evaluationPath.substring(compRoot.length + 1 + branchIdx.toString().length)
  return isBranchRedirectToType(rest)
}

/**
 * Returns `(compRoot, branchIdx)` for the **leftmost** `/oneOf/` or `/anyOf/` segment in the
 * evaluationPath — the outermost composition. Used by enum-merge grouping so nested
 * anyOf/oneOf trees flatten into a single union at the top level.
 */
fun extractOutermostComposition(evaluationPath: String?): Pair<String, Int>? {
  if (evaluationPath.isNullOrEmpty()) return null
  val oneOfIdx = evaluationPath.indexOf("/oneOf/")
  val anyOfIdx = evaluationPath.indexOf("/anyOf/")
  val idx = when {
    oneOfIdx < 0 && anyOfIdx < 0 -> return null
    oneOfIdx < 0 -> anyOfIdx
    anyOfIdx < 0 -> oneOfIdx
    else -> minOf(oneOfIdx, anyOfIdx)
  }
  val keywordLen = if (oneOfIdx == idx) "/oneOf".length else "/anyOf".length
  val compRoot = evaluationPath.substring(0, idx + keywordLen)
  val branchStart = idx + keywordLen + 1
  val nextSlash = evaluationPath.indexOf('/', branchStart)
  val branchSegment = if (nextSlash >= 0) evaluationPath.substring(branchStart, nextSlash)
                      else evaluationPath.substring(branchStart)
  val branchIdx = branchSegment.toIntOrNull() ?: return null
  return compRoot to branchIdx
}

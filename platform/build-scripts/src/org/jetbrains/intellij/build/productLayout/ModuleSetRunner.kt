// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Path

/**
 * Parses JSON argument from command line in the format --json or --json='{"filter":"...","value":"..."}'.
 * Returns null for full JSON output, or JsonFilter for filtered output.
 *
 * @param arg The command line argument (e.g., "--json" or "--json={...}")
 * @return JsonFilter if filter is specified, null for full JSON output
 */
fun parseJsonArgument(arg: String): JsonFilter? {
  return if (arg.contains("=")) {
    val filterJson = arg.substringAfter("=")
    try {
      Json.decodeFromString<JsonFilter>(filterJson)
    }
    catch (e: Exception) {
      System.err.println("Failed to parse JSON filter: $filterJson")
      System.err.println("Error: ${e.message}")
      null
    }
  }
  else {
    null // Full JSON output
  }
}

/**
 * Generic main runner for module set generation and analysis.
 * Supports two modes:
 * 1. JSON mode (--json): Outputs comprehensive analysis as JSON to stdout
 * 2. Default mode: Generates XML files for module sets and products
 *
 * This is the generic orchestration logic that was previously duplicated in UltimateModuleSets.kt.
 * Now module set providers (community, ultimate) can call this function with their specific data.
 *
 * @param args Command line arguments
 * @param communityModuleSets Module sets from community
 * @param ultimateModuleSets Module sets from ultimate (or empty for community-only)
 * @param communitySourceFile Source file path for community module sets
 * @param ultimateSourceFile Source file path for ultimate module sets (or null for community-only)
 * @param projectRoot Project root path
 * @param generateXmlImpl Lambda to generate XML files (default mode implementation)
 */
fun runModuleSetMain(
  args: Array<String>,
  communityModuleSets: List<ModuleSet>,
  ultimateModuleSets: List<ModuleSet>,
  communitySourceFile: String,
  ultimateSourceFile: String?,
  projectRoot: Path,
  generateXmlImpl: suspend () -> Unit
) {
  // Parse --json arg with optional filter
  val jsonArg = args.firstOrNull { it.startsWith("--json") }

  when {
    jsonArg != null -> {
      runBlocking(Dispatchers.Default) {
        // Prepare all module sets with metadata
        val communityModuleSetsWithMeta = communityModuleSets.map {
          ModuleSetMetadata(it, "community", communitySourceFile)
        }
        val ultimateModuleSetsWithMeta = if (ultimateSourceFile != null) {
          ultimateModuleSets.map {
            ModuleSetMetadata(it, "ultimate", ultimateSourceFile)
          }
        } else {
          emptyList()
        }
        val allModuleSets = communityModuleSetsWithMeta + ultimateModuleSetsWithMeta

        // Discover all products (reuse existing logic from productXmlFileGenerator)
        val products = discoverAllProductsForJson(projectRoot)

        // Parse filter from --json='{"filter":"..."}' format
        val filter = parseJsonArgument(jsonArg)

        // Stream JSON to stdout
        streamModuleAnalysisJson(allModuleSets, products, projectRoot, filter)
      }
    }
    else -> {
      // Default mode: Generate XML files
      runBlocking(Dispatchers.Default) {
        generateXmlImpl()
      }
    }
  }
}

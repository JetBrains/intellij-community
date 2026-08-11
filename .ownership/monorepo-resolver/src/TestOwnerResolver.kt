// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeowners.monorepo.resolver

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.io.path.useLines

/**
 * Resolves code owners of test classes from the `test-class-owners.ndjson` artifact — a map of test class FQN
 * to owner group precomputed at build time (see `TestClassOwners` in `intellij.idea.ultimate.build`).
 *
 * A class FQN is unique across the monorepo (enforced by `IntelliJProjectTestNamesUniquenessTest`), so the FQN
 * alone identifies the source file and therefore the owner — no module or file location is needed at lookup time.
 */
class TestOwnerResolver(private val ownersByClassFqn: Map<String, String>) {

  /** Owner group name for [classFqn]; nested classes (`Outer$Inner`) resolve through their top-level class. */
  fun getOwner(classFqn: String): String? {
    ownersByClassFqn[classFqn]?.let { return it }
    val topLevelFqn = classFqn.substringBefore('$')
    return if (topLevelFqn != classFqn) ownersByClassFqn[topLevelFqn] else null
  }

  companion object {
    /** File name of the FQN→owner artifact, generated to `out/artifacts/codeowners/`. */
    const val TEST_CLASS_OWNERS_FILE_NAME: String = "test-class-owners.ndjson"

    private val logger: Logger = Logger.getLogger(TestOwnerResolver::class.java.name)
    private val json = Json { ignoreUnknownKeys = true }

    fun create(testClassOwners: Path): TestOwnerResolver? {
      if (!Files.exists(testClassOwners)) return null
      return try {
        val map = HashMap<String, String>()
        testClassOwners.useLines { lines ->
          for (line in lines) {
            if (line.isBlank()) continue
            try {
              val entry = json.decodeFromString<TestClassOwnerEntry>(line)
              map[entry.fqn] = entry.owner
            }
            catch (e: Exception) {
              logger.warning("Failed to parse test class owner entry: $line - ${e.message}")
            }
          }
        }
        TestOwnerResolver(map)
      }
      catch (e: Exception) {
        logger.warning("Failed to create TestOwnerResolver: ${e.message}")
        null
      }
    }
  }
}

/** One row of `test-class-owners.ndjson`: a top-level class FQN in a test output root and its owner group. */
@Serializable
data class TestClassOwnerEntry(
  @SerialName("fqn") val fqn: String,
  @SerialName("owner") val owner: String,
)

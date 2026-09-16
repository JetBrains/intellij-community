// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/**
 * The derivation under `org.jetbrains.intellij.build.devDist` owns its copy of the packing convention.
 *
 * The packaging gate compares two producers: the derivation and the distribution build. A derivation that calls
 * the build's own placement code compares the build against itself, and the gate can then never fail. ADR 0008
 * records this in the rejected alternative "Calling `computeOutputJarPath`", in
 * `build/decisions/0008-the-content-leaf-follows-the-descriptor-leaf.md`.
 *
 * So no source under the derivation directory may name a member of [FORBIDDEN]. The scan reads code only. A
 * comment may name a forbidden member, for example to say what the code does not call.
 */
class DevDistDerivationIndependenceTest {
  @Test
  fun `the derivation does not call the placement code of the distribution build`() {
    val root = COMMUNITY_ROOT.communityRoot
    val derivationDir = root.resolve(DERIVATION_DIR_UNDER_COMMUNITY)
    // the directory may not exist yet, and an empty scan passes
    val sources = if (Files.isDirectory(derivationDir)) {
      derivationDir.walk().filter { it.extension == "kt" }.sorted().toList()
    }
    else {
      emptyList()
    }

    val violations = sources.flatMap { file -> findViolations(file, root) }
    assertThat(violations)
      .withFailMessage {
        "The dev-dist derivation must not use the placement code of the distribution build (ADR 0008):\n" +
        violations.joinToString(separator = "\n")
      }
      .isEmpty()
  }

  private fun findViolations(file: Path, root: Path): List<String> {
    val code = stripComments(file.readText())
    val violations = ArrayList<String>()
    code.lineSequence().forEachIndexed { index, line ->
      for (forbidden in FORBIDDEN) {
        if (forbidden.pattern.containsMatchIn(line)) {
          violations.add("${file.relativeTo(root)}:${index + 1}: ${forbidden.name}")
        }
      }
    }
    return violations
  }
}

private const val DERIVATION_DIR_UNDER_COMMUNITY = "platform/build-scripts/src/org/jetbrains/intellij/build/devDist"

/**
 * One forbidden member. [pattern] matches the whole identifier only, so a longer identifier with the same prefix
 * does not trip it.
 */
private class Forbidden(val name: String, val pattern: Regex) {
  constructor(identifier: String) : this(name = identifier, pattern = Regex("\\b${Regex.escape(identifier)}\\b"))
}

/**
 * The members of the distribution build that the derivation must not call or import.
 *
 * The derivation may read the `PluginLayout` and `PlatformLayout` accessors, such as `includedModules`,
 * `getMainJarName`, `directoryName`, `getIncludedProjectLibraries`, `getIncludedModuleLibraries`,
 * `getModulesWithExcludedModuleLibraries`, `getExcludedModuleLibraries`, `getResourceGeneratorProjectLibraries`,
 * `pathsToScramble` and `mainModule`. It may read `frontendIncompatibleRootModuleNames()` as a list, the JPS model,
 * a `ModuleOutputProvider`, and a descriptor file under a module source root. None of those is in this list.
 */
private val FORBIDDEN: List<Forbidden> = listOf(
  Forbidden("BuildContext"),
  Forbidden("readDevDistPlatformJarsTable"),
  Forbidden("readDevDistDistFilesTable"),
  Forbidden("writeDevDistPlatformJarsTable"),
  Forbidden("computeOutputJarPath"),
  Forbidden("computeEmbeddedOutputJarPath"),
  Forbidden("getDefaultJarName"),
  Forbidden("needsSeparateJar"),
  Forbidden("checkNeedsSeparateJar"),
  Forbidden("isPluginModulePackedIntoSeparateJar"),
  Forbidden("JarPackagerDependencyHelper"),
  Forbidden("JpsProductModeMatcher"),
  Forbidden("FrontendModuleFilterImpl"),
  Forbidden("createFrontendModuleFilter"),
  Forbidden("FrontendModuleFilter"),
  Forbidden("XIncludeElementResolverImpl"),
  Forbidden("PluginXmlPatcher"),
  Forbidden("JarPackager"),
  Forbidden("getJarAsset"),
  Forbidden("inferModuleSources"),
  Forbidden("ContentReport"),
  Forbidden("ParsedContentReport"),
  Forbidden("DistributionBuilderState"),
  Forbidden("pluginAuto"),
  Forbidden("convertModuleNameToFileName"),
  Forbidden("computeSourcesForModuleLibs"),
  // the PluginLayout(mainModule) constructor is a placement default; the type name alone stays allowed
  Forbidden(name = "PluginLayout(...) constructor", pattern = Regex("\\bPluginLayout\\s*\\(")),
)

/**
 * Removes every block comment and line comment, and keeps the line count.
 *
 * A removed block comment leaves its line breaks in place, so a reported line number matches the file.
 */
private fun stripComments(text: String): String {
  val out = StringBuilder(text.length)
  var i = 0
  while (i < text.length) {
    when {
      text.startsWith("/*", i) -> {
        var depth = 1
        i += 2
        while (i < text.length && depth > 0) {
          when {
            text.startsWith("/*", i) -> {
              depth++
              i += 2
            }
            text.startsWith("*/", i) -> {
              depth--
              i += 2
            }
            else -> {
              if (text[i] == '\n') {
                out.append('\n')
              }
              i++
            }
          }
        }
      }
      text.startsWith("//", i) -> {
        while (i < text.length && text[i] != '\n') {
          i++
        }
      }
      else -> {
        out.append(text[i])
        i++
      }
    }
  }
  return out.toString()
}

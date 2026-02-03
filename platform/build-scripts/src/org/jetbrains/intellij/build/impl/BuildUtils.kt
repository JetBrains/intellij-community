// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.text.StringUtilRt
import org.jetbrains.intellij.build.dependencies.TeamCityHelper
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiConsumer

object BuildUtils {
  fun replaceAll(text: String, replacements: Map<String, String>, marker: String = "__"): String {
    var result = text
    replacements.forEach(BiConsumer { k, v ->
      result = result.replace("$marker${k}$marker", v)
    })
    return result
  }

  fun checkedReplace(oldText: String, regex: String, newText: String, regexOptions: Set<RegexOption> = emptySet()): String {
    val result = oldText.replaceFirst(Regex(regex, regexOptions), newText)
    if (result == oldText) {
      if (oldText.contains(newText) && !TeamCityHelper.isUnderTeamCity) {
        // Locally, e.g., in 'Update IDE from Sources' allow data to be already present
        return result
      }

      throw IllegalStateException("Cannot find '$regex' in '$oldText'")
    }
    return result
  }

  fun copyAndPatchFile(sourcePath: Path, targetPath: Path, replacements: Map<String, String>, marker: String = "__", lineSeparator: String = "") {
    Files.createDirectories(targetPath.parent)
    var content = replaceAll(Files.readString(sourcePath), replacements, marker)
    if (!lineSeparator.isEmpty()) {
      content = StringUtilRt.convertLineSeparators(content, lineSeparator)
    }
    Files.writeString(targetPath, content)
  }

  fun getPluginJars(pluginPath: Path): List<Path> {
    return Files.newDirectoryStream(pluginPath.resolve("lib"), "*.jar").use { it.toList() }
  }
}
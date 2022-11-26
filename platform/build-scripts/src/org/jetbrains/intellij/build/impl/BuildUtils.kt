// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.text.StringUtilRt
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiConsumer

object BuildUtils {
  @JvmStatic
  @JvmOverloads
  fun replaceAll(text: String, replacements: Map<String, String>, marker: String = "__"): String {
    var result = text
    replacements.forEach(BiConsumer { k, v ->
      result = result.replace("$marker${k}$marker", v)
    })
    return result
  }

  @JvmStatic
  @JvmOverloads
  fun copyAndPatchFile(sourcePath: Path,
                       targetPath: Path,
                       replacements: Map<String, String>,
                       marker: String = "__",
                       lineSeparator: String = "") {
    Files.createDirectories(targetPath.parent)
    var content = replaceAll(Files.readString(sourcePath), replacements, marker)
    if (!lineSeparator.isEmpty()) {
      content = StringUtilRt.convertLineSeparators(content, lineSeparator)
    }
    Files.writeString(targetPath, content)
  }

  fun propertiesToJvmArgs(properties: List<Pair<String, String>>): List<String> {
    val result = ArrayList<String>(properties.size)
    for ((key, value) in properties) {
      addVmProperty(result, key, value.toString())
    }
    return result
  }

  internal fun addVmProperty(args: MutableList<String>, key: String, value: String?) {
    if (value != null) {
      args.add("-D$key=$value")
    }
  }

  @JvmStatic
  fun getPluginJars(pluginPath: Path): List<Path> {
    return Files.newDirectoryStream(pluginPath.resolve("lib"), "*.jar").use { it.toList() }
  }

  val isUnderJpsBootstrap: Boolean
    get() = System.getenv("JPS_BOOTSTRAP_COMMUNITY_HOME") != null
}

fun convertLineSeparators(file: Path, newLineSeparator: String) {
  val data = Files.readString(file)
  val convertedData = StringUtilRt.convertLineSeparators(data, newLineSeparator)
  if (data != convertedData) {
    Files.writeString(file, convertedData)
  }
}
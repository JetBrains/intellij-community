// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.text.StringUtilRt
import java.io.PrintStream
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

  //if the build script is running under Ant or AntBuilder it may replace the standard System.out
  @JvmStatic
  val realSystemOut: PrintStream // No longer works in recent Ant 1.9.x and 1.10
    get() {
      try {
        val aClass = BuildUtils::class.java.classLoader.loadClass("org.jetbrains.jps.gant.GantWithClasspathTask")
        val result = aClass.getDeclaredField("out")
        result.isAccessible = true
        val out = result.get(null)
        if (out != null) {
          return out as PrintStream
        }
      }
      catch (ignored: Throwable) {
      }

      return System.out
    }

  @JvmStatic
  fun propertiesToJvmArgs(properties: Map<String, Any>): List<String> {
    val result = ArrayList<String>(properties.size)
    for ((key, value) in properties) {
      addVmProperty(result, key, value.toString())
    }
    return result
  }

  @JvmStatic
  fun addVmProperty(args: MutableList<String>, key: String, value: String?) {
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
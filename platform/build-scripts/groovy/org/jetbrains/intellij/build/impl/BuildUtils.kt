// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.text.StringUtilRt
import org.apache.tools.ant.Main
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
  fun replaceAll(file: Path?, marker: String, vararg replacements: String) {
    var text = Files.readString(file)
    var i = 0
    while (i < replacements.size) {
      text = text.replace(marker + replacements[i] + marker, replacements[i + 1])
      i += 2
    }
    Files.writeString(file, text)
  }

  @JvmStatic
  fun replaceAll(text: String, marker: String, vararg replacements: String): String {
    var result = text
    var i = 0
    while (i < replacements.size) {
      result = result.replace(marker + replacements[i] + marker, replacements[i + 1])
      i += 2
    }
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

  @JvmStatic
  fun assertUnixLineEndings(file: Path) {
    check(!Files.readString(file).contains('\r')) { "Text file must not contain \r (CR or CRLF) line endings: $file" }
  }

  //if the build script is running under Ant or AntBuilder it may replace the standard System.out
  @JvmStatic
  val realSystemOut: PrintStream // No longer works in recent Ant 1.9.x and 1.10
    get() {
      try {
        // if the build script is running under Ant or AntBuilder it may replace the standard System.out
        val result = Main::class.java.getDeclaredField("out")
        result.isAccessible = true
        // No longer works in recent Ant 1.9.x and 1.10
        return result.get(null) as PrintStream
      }
      catch (ignored: Throwable) {
      }

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
  fun convertLineSeparators(file: Path, newLineSeparator: String) {
    val data = Files.readString(file)
    val convertedData = StringUtilRt.convertLineSeparators(data, newLineSeparator)
    if (data != convertedData) {
      Files.writeString(file, convertedData)
    }
  }

  @JvmStatic
  fun getPluginJars(pluginPath: Path): List<Path> {
    return Files.newDirectoryStream(pluginPath.resolve("lib"), "*.jar").use { it.toList() }
  }

  val isUnderJpsBootstrap: Boolean
    get() = System.getenv("JPS_BOOTSTRAP_COMMUNITY_HOME") != null
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.platform.buildScripts.devLaunch

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.buildData.productInfo.CustomCommandLaunchData
import com.intellij.platform.buildData.productInfo.ProductInfoData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.inputStream
import kotlin.io.path.readLines

private const val PRODUCT_INFO_FILE_NAME = "product-info.json"

private val productInfoJson = Json { ignoreUnknownKeys = true }

/**
 * Returns system properties which should be set for the IDE process.
 * This function should be used only if the IDE is started in the same process.
 * It's better to start the IDE in a separate process and use [readVmOptions] to pass all necessary VM options to it.
 */
@ApiStatus.Internal
fun getIdeSystemProperties(runDir: Path): Map<String, String> {
  val result = LinkedHashMap<String, String>()

  val properties = Properties()
  // we need this only because PathManager take idea.properties to the sources if 'idea.use.dev.build.server' is set to 'true'
  Files.newInputStream(runDir.resolve("bin/idea.properties")).use { properties.load(it) }
  for ((key, value) in properties) {
    result.put(key.toString(), value.toString())
  }

  readVmOptions(runDir).asSequence()
    .filter { it.startsWith("-D") }
    .map { it.removePrefix("-D") }
    .associateByTo(result, { it.substringBefore('=') }, { it.substringAfter('=', "") })

  return result
}

/**
 * The lines of the main vmoptions file of the distribution at [runDir], then `-Djb.vmOptionsFile`, then the additional JVM
 * arguments of the first launch. [mainVmOptionsFile] finds the file.
 */
@ApiStatus.Internal
fun readVmOptions(runDir: Path): List<String> {
  val result = ArrayList<String>()

  val productInfoFile = findProductInfoFile(runDir)
  val productInfo = readProductInfo(productInfoFile)
  val vmOptionsFile = mainVmOptionsFile(runDir, productInfoFile, productInfo)
  result.addAll(vmOptionsFile.readLines())
  result.add("-Djb.vmOptionsFile=${vmOptionsFile}")

  productInfo.launch.first().additionalJvmArguments.mapTo(result) { resolveIdeHomeMacro(it, runDir) }

  return result
}

/**
 * The main vmoptions file of the distribution at [runDir], as the native launcher finds it.
 *
 * It is `vmOptionsFilePath` of the first launch of [productInfo], relative to the directory of [productInfoFile] when that is
 * a production place. The `bin/product-info.json` of the dev build of `IdeBuilder` holds the text of the production place of
 * this OS, so [PRODUCTION_PRODUCT_INFO_PATH] gives its base. So a vmoptions file of an embedded frontend in `bin` does not
 * matter. A path that leaves [runDir] fails.
 */
private fun mainVmOptionsFile(runDir: Path, productInfoFile: Path, productInfo: ProductInfoData): Path {
  val launch = requireNotNull(productInfo.launch.firstOrNull()) { "$productInfoFile states no launch" }
  val path = launch.vmOptionsFilePath
  val home = runDir.normalize()
  val base = if (productInfoFile == home.resolve(DEV_BUILD_PRODUCT_INFO_PATH)) home.resolve(PRODUCTION_PRODUCT_INFO_PATH).parent else productInfoFile.parent
  val file = base.resolve(path).normalize()
  require(!Path.of(path).isAbsolute && file.startsWith(home) && file != home) {
    "The vmOptionsFilePath '$path' of $productInfoFile is not a file of $home"
  }
  return file
}

/**
 * Returns the product-info.json `customCommands` entry which handles [command],
 * or `null` if the build doesn't declare such a command.
 */
@ApiStatus.Internal
fun readCustomCommand(runDir: Path, command: String): CustomCommandLaunchData? {
  // multi-architecture builds are not generated for dev builds
  return readProductInfo(findProductInfoFile(runDir)).launch.single().customCommands.find { command in it.commands }
}

/**
 * [CustomCommandLaunchData.additionalJvmArguments] with the `IDE_HOME` macro resolved against [runDir].
 */
@ApiStatus.Internal
fun CustomCommandLaunchData.resolveAdditionalJvmArguments(runDir: Path): List<String> =
  additionalJvmArguments.map { resolveIdeHomeMacro(it, runDir) }

/**
 * The system properties of the `-D` arguments in [jvmArguments], the JVM arguments of a custom command with the macros resolved.
 * A property without `=` has an empty value. A value that still holds a `$` macro fails.
 */
@ApiStatus.Internal
fun customCommandSystemProperties(jvmArguments: List<String>): Map<String, String> {
  val result = LinkedHashMap<String, String>()
  for (argument in jvmArguments) {
    if (!argument.startsWith("-D")) {
      continue
    }
    val property = argument.removePrefix("-D")
    val value = property.substringAfter('=', "")
    check('$' !in value) { "Unsubstituted macro in JVM argument: $property" }
    result.put(property.substringBefore('='), value)
  }
  return result
}

/**
 * The main class and the system properties of the custom command that handles [command] in the distribution at [runDir].
 * `PreBuiltDevMain` calls it through reflection, so the result is a JDK type.
 */
@ApiStatus.Internal
@Suppress("unused")
fun readCustomCommandLaunch(runDir: Path, command: String): Map.Entry<String, Map<String, String>> {
  val launch = readCustomCommand(runDir, command) ?: error("No custom command found for $command")
  val mainClass = checkNotNull(launch.mainClass) { "The custom command '$command' names no main class" }
  return java.util.AbstractMap.SimpleImmutableEntry(mainClass, customCommandSystemProperties(launch.resolveAdditionalJvmArguments(runDir)))
}

private fun readProductInfo(file: Path): ProductInfoData {
  return file.inputStream().buffered().use {
    productInfoJson.decodeFromStream(ProductInfoData.serializer(), it)
  }
}

private const val MAC_PRODUCT_INFO_PATH = "Resources/$PRODUCT_INFO_FILE_NAME"

/** The place of `product-info.json` in the dev build of `IdeBuilder`. */
private const val DEV_BUILD_PRODUCT_INFO_PATH = "bin/$PRODUCT_INFO_FILE_NAME"

/** The production place of `product-info.json` relative to the home: `Resources/product-info.json` on macOS, else the root. */
private val PRODUCTION_PRODUCT_INFO_PATH: String = if (SystemInfoRt.isMac) MAC_PRODUCT_INFO_PATH else PRODUCT_INFO_FILE_NAME

/**
 * The `product-info.json` file of the distribution at [runDir].
 *
 * The first candidate is the production place of this OS: `Resources/product-info.json` on macOS, `product-info.json` on
 * other systems. The second is the production place of the other layout, which a distribution cross-built for a Linux
 * container on macOS has. The third is `bin/product-info.json`, which the dev build of `IdeBuilder` writes.
 * When no file exists, the result is the first candidate, so that the read error names it.
 */
private fun findProductInfoFile(runDir: Path): Path {
  val productionFile = runDir.resolve(PRODUCTION_PRODUCT_INFO_PATH)
  val candidates = listOf(productionFile, runDir.resolve(if (SystemInfoRt.isMac) PRODUCT_INFO_FILE_NAME else MAC_PRODUCT_INFO_PATH), runDir.resolve(DEV_BUILD_PRODUCT_INFO_PATH))
  return candidates.firstOrNull { Files.isRegularFile(it) } ?: productionFile
}

/**
 * Substitutes the OS-specific `IDE_HOME` macro used in product-info.json JVM arguments with [runDir].
 */
private fun resolveIdeHomeMacro(jvmArgument: String, runDir: Path): String {
  val macroName = when {
    SystemInfoRt.isWindows -> "%IDE_HOME%"
    SystemInfoRt.isMac -> $$"$APP_PACKAGE/Contents"
    else -> $$"$IDE_HOME"
  }
  return jvmArgument.replace(macroName, runDir.toString())
}

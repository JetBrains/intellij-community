// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.dev

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.VmProperties
import org.jetbrains.intellij.build.closeKtorClient
import org.jetbrains.intellij.build.impl.productInfo.PRODUCT_INFO_FILE_NAME
import org.jetbrains.intellij.build.productLayout.discovery.PRODUCT_REGISTRY_PATH
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfiguration
import org.jetbrains.intellij.build.productLayout.discovery.ProductConfigurationRegistry
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.use
import tools.jackson.core.JsonToken
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.json.JsonFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.inputStream
import kotlin.io.path.readLines

/**
 * Custom path for product properties
 */
private const val CUSTOM_PRODUCT_PROPERTIES_PATH = "idea.product.properties.path"

/**
 * Returns system properties which should be set for the IDE process.
 * This function should be used only if the IDE is started in the same process.
 * It's better to start the IDE in a separate process and use [readVmOptions] to pass all necessary VM options to it. 
 */
fun getIdeSystemProperties(runDir: Path): VmProperties {
  val result = LinkedHashMap<String, String>()

  val properties = Properties()
  // we need this only because PathManager take idea.properties to the sources if 'idea.use.dev.build.server' is set to 'true'
  properties.load(Files.newInputStream(runDir.resolve("bin/idea.properties")))
  for (property in properties) {
    result.put(property.key.toString(), property.value.toString())
  }

  val vmOptions = readVmOptions(runDir)
  vmOptions.asSequence()
    .filter { it.startsWith("-D") }
    .map { it.removePrefix("-D") }
    .associateByTo(result, { it.substringBefore('=') }, { it.substringAfter('=', "") })
  
  return VmProperties(result)
}

/**
 * Extracts additionalJvmArguments from the first launch configuration in product-info.json using Jackson streaming parser.
 * This avoids loading the entire JSON structure into memory when we only need a small subset of data.
 */
private fun extractAdditionalJvmArguments(productInfoFile: Path): List<String> {
  val result = mutableListOf<String>()
  val jsonFactory = JsonFactory()

  productInfoFile.inputStream().use { input ->
    jsonFactory.createParser(ObjectReadContext.empty(), input).use { parser ->
      // Find the "launch" array
      while (parser.nextToken() != null) {
        if (parser.currentToken() == JsonToken.PROPERTY_NAME && parser.currentName() == "launch") {
          // Move to START_ARRAY
          parser.nextToken()
          // Move to first object in array (START_OBJECT)
          parser.nextToken()

          // Find "additionalJvmArguments" in the first launch config
          while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() == JsonToken.PROPERTY_NAME && parser.currentName() == "additionalJvmArguments") {
              // Move to START_ARRAY
              parser.nextToken()

              // Read all string values from the array
              while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() == JsonToken.VALUE_STRING) {
                  result.add(parser.string)
                }
              }

              // We found what we need, stop parsing
              return result
            }
          }
        }
      }
    }
  }

  return result
}

fun readVmOptions(runDir: Path): List<String> {
  val result = ArrayList<String>()

  val binDir = runDir.resolve("bin")
  val vmOptionsFile = Files.newDirectoryStream(binDir, "*.vmoptions").use { it.singleOrNull() }
  requireNotNull(vmOptionsFile) {
    "No single *.vmoptions file in $binDir (${Files.newDirectoryStream(binDir).use { it.asSequence().map(Path::getFileName).joinToString() }})"
  }
  result.addAll(vmOptionsFile.readLines())
  result.add("-Djb.vmOptionsFile=${vmOptionsFile}")

  val productInfoFile = runDir.resolve("bin").resolve(PRODUCT_INFO_FILE_NAME)
  val macroName = when (OsFamily.currentOs) {
    OsFamily.WINDOWS -> "%IDE_HOME%"
    OsFamily.MACOS -> $$"$APP_PACKAGE/Contents"
    OsFamily.LINUX -> $$"$IDE_HOME"
  }
  extractAdditionalJvmArguments(productInfoFile).mapTo(result) { it.replace(macroName, runDir.toString()) }

  return result
}

// returns IDE installation directory
suspend fun buildProductInProcess(request: BuildRequest): Path {
  request.tracer?.let {
    TraceManager.setTracer(it)
  }
  return TraceManager.spanBuilder("build ide").setAttribute("request", request.toString()).use {
    try {
      buildProduct(
        request = request,
        createProductProperties = { compilationContext ->
          val configuration = createConfiguration(homePath = request.projectDir, productionClassOutput = request.productionClassOutput)
          val productConfiguration = getProductConfiguration(configuration, request.platformPrefix, request.baseIdePlatformPrefixForFrontend)
          createProductProperties(
            productConfiguration = productConfiguration,
            outputProvider = compilationContext.outputProvider,
            projectDir = request.projectDir,
            platformPrefix = request.platformPrefix,
          )
        },
      )
    }
    finally {
      // otherwise, a thread leak in tests
      if (!request.keepHttpClient) {
        withContext(NonCancellable) {
          closeKtorClient()
        }
      }
    }
  }
}

private fun createConfiguration(productionClassOutput: Path, homePath: Path): ProductConfigurationRegistry {
  // for compatibility with local runs and runs on CI
  if (System.getProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY) == null) {
    System.setProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY, productionClassOutput.parent.toString())
  }

  val projectPropertiesPath = getProductPropertiesPath(homePath)
  return Json.decodeFromString(Files.readString(projectPropertiesPath))
}

internal fun getProductPropertiesPath(homePath: Path): Path {
  // handle a custom product properties path
  return System.getProperty(CUSTOM_PRODUCT_PROPERTIES_PATH)?.let { homePath.resolve(it) }?.takeIf { Files.exists(it) }
         ?: homePath.resolve(PRODUCT_REGISTRY_PATH)
}

private fun getProductConfiguration(configuration: ProductConfigurationRegistry, platformPrefix: String, baseIdePlatformPrefixForFrontend: String?): ProductConfiguration {
  val key = if (baseIdePlatformPrefixForFrontend == null) platformPrefix else "$baseIdePlatformPrefixForFrontend$platformPrefix"
  return configuration.products.get(key)
         ?: throw ConfigurationException("No production configuration for `$key`; please add to `${PRODUCT_REGISTRY_PATH}` if needed")
}

internal class ConfigurationException(message: String) : RuntimeException(message)

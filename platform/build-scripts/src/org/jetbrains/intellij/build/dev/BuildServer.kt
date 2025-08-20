// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.jetbrains.intellij.build.dev

import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.buildData.productInfo.ProductInfoData
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.VmProperties
import org.jetbrains.intellij.build.closeKtorClient
import org.jetbrains.intellij.build.impl.productInfo.PRODUCT_INFO_FILE_NAME
import org.jetbrains.intellij.build.impl.productInfo.jsonEncoder
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.readLines

@Serializable
internal data class Configuration(@JvmField val products: Map<String, ProductConfiguration>)

@Serializable
internal data class ProductConfiguration(@JvmField val modules: List<String>, @JvmField @SerialName("class") val className: String)

private const val PRODUCTS_PROPERTIES_PATH = "build/dev-build.json"

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
  //we need this only because PathManager take idea.properties to the sources if 'idea.use.dev.build.server' is set to 'true'
  properties.load(Files.newInputStream(runDir.resolve("bin/idea.properties")))
  for (property in properties) {
    result.put(property.key.toString(), property.value.toString())
  }

  val vmOptions = readVmOptions(runDir)
  vmOptions.asSequence()
    .filter { it.startsWith("-D") }
    .map { it.removePrefix("-D") }
    .associateByTo(result, { it.substringBefore("=") }, { it.substringAfter("=", "") })
  
  return VmProperties(result)
}

fun readVmOptions(runDir: Path): List<String> {
  val result = ArrayList<String>()

  val vmOptionsFile = Files.newDirectoryStream(runDir.resolve("bin"), "*.vmoptions").use { it.singleOrNull() }
  require(vmOptionsFile != null) {
    "No single *.vmoptions file in ${runDir} (${NioFiles.list(runDir).map(Path::getFileName).joinToString()})}"
  }
  result.addAll(vmOptionsFile.readLines())
  result.add("-Djb.vmOptionsFile=${vmOptionsFile}")

  val productInfoFile = runDir.resolve("bin").resolve(PRODUCT_INFO_FILE_NAME)
  if (productInfoFile.exists()) {
    val productJson = productInfoFile.inputStream().use { jsonEncoder.decodeFromStream<ProductInfoData>(it) }
    val macroName = when (OsFamily.currentOs) {
      OsFamily.WINDOWS -> "%IDE_HOME%"
      OsFamily.MACOS -> "\$APP_PACKAGE/Contents"
      OsFamily.LINUX -> "\$IDE_HOME"
    }
    result.addAll(productJson.launch[0].additionalJvmArguments.map { it.replace(macroName, runDir.toString()) })
  }

  return result
}

/** Returns IDE installation directory */
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
          createProductProperties(productConfiguration = productConfiguration, compilationContext = compilationContext, request = request)
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

private fun createConfiguration(productionClassOutput: Path, homePath: Path): Configuration {
  // for compatibility with local runs and runs on CI
  if (System.getProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY) == null) {
    System.setProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY, productionClassOutput.parent.toString())
  }

  val projectPropertiesPath = getProductPropertiesPath(homePath)
  return Json.decodeFromString(Configuration.serializer(), Files.readString(projectPropertiesPath))
}

internal fun getProductPropertiesPath(homePath: Path): Path {
  // handle a custom product properties path
  return System.getProperty(CUSTOM_PRODUCT_PROPERTIES_PATH)?.let { homePath.resolve(it) }?.takeIf { Files.exists(it) }
         ?: homePath.resolve(PRODUCTS_PROPERTIES_PATH)
}

private fun getProductConfiguration(configuration: Configuration, platformPrefix: String, baseIdePlatformPrefixForFrontend: String?): ProductConfiguration {
  val key = if (baseIdePlatformPrefixForFrontend != null) "$baseIdePlatformPrefixForFrontend$platformPrefix" else platformPrefix
  return configuration.products[key]
         ?: throw ConfigurationException("No production configuration for `$key`; please add to `${PRODUCTS_PROPERTIES_PATH}` if needed")
}

internal class ConfigurationException(message: String) : RuntimeException(message)

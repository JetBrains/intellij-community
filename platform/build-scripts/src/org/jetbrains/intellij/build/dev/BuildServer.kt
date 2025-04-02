// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.intellij.build.JvmArchitecture
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

@Deprecated("Prefer `readVmOptions` for more accurate result")
@Suppress("SpellCheckingInspection")
fun getIdeSystemProperties(runDir: Path): VmProperties {
  val result = LinkedHashMap<String, String>()

  val properties = Properties()
  properties.load(Files.newInputStream(runDir.resolve("bin/idea.properties")))
  for (property in properties) {
    result.put(property.key.toString(), property.value.toString())
  }

  // see BuildContextImpl.getAdditionalJvmArguments - we should somehow deduplicate code
  val libDir = runDir.resolve("lib")
  result.putAll(
    listOf(
      "jna.boot.library.path" to "$libDir/jna/${JvmArchitecture.currentJvmArch.dirName}",
      "pty4j.preferred.native.folder" to "$libDir/pty4j",
      // require bundled JNA dispatcher lib
      "jna.nosys" to "true",
      "jna.noclasspath" to "true",
      "jb.vmOptionsFile" to "${Files.newDirectoryStream(runDir.resolve("bin"), "*.vmoptions").use { it.single() }}",
      "compose.swing.render.on.graphics" to "true",
      "io.netty.allocator.type" to "pooled",
    )
  )
  return VmProperties(result)
}

fun readVmOptions(runDir: Path): List<String> {
  val result = ArrayList<String>()

  val vmOptionsFile = Files.newDirectoryStream(runDir.resolve("bin"), "*.vmoptions").use { it.singleOrNull() }
  require(vmOptionsFile != null) {
    "No single *.vmoptions file in ${runDir} (${NioFiles.list(runDir).map(Path::getFileName).joinToString()})}"
  }
  result += vmOptionsFile.readLines()
  result += "-Djb.vmOptionsFile=${vmOptionsFile}"

  val productInfoFile = runDir.resolve("bin").resolve(PRODUCT_INFO_FILE_NAME)
  if (productInfoFile.exists()) {
    val productJson = productInfoFile.inputStream().use { jsonEncoder.decodeFromStream<ProductInfoData>(it) }
    val macroName = when (OsFamily.currentOs) {
      OsFamily.WINDOWS -> "%IDE_HOME%"
      OsFamily.MACOS -> "\$APP_PACKAGE/Contents"
      OsFamily.LINUX -> "\$IDE_HOME"
    }
    result += productJson.launch[0].additionalJvmArguments.map { it.replace(macroName, runDir.toString()) }
  }

  return result
}

/** Returns IDE installation directory */
suspend fun buildProductInProcess(request: BuildRequest): Path {
  if (request.tracer != null) {
    TraceManager.setTracer(request.tracer)
  }
  return TraceManager.spanBuilder("build ide").setAttribute("request", request.toString()).use {
    try {
      buildProduct(
        request = request,
        createProductProperties = { compilationContext ->
          val configuration = createConfiguration(homePath = request.projectDir, productionClassOutput = request.productionClassOutput)
          val productConfiguration = getProductConfiguration(configuration, request.platformPrefix)
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

private fun getProductConfiguration(configuration: Configuration, platformPrefix: String): ProductConfiguration {
  return configuration.products[platformPrefix]
         ?: throw ConfigurationException("No production configuration for platform prefix `${platformPrefix}`; please add to `${PRODUCTS_PROPERTIES_PATH}` if needed")
}

internal class ConfigurationException(message: String) : RuntimeException(message)

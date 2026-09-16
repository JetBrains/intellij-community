@file:JvmName("DevPluginPrepareMain")

package org.jetbrains.intellij.build.devServer

import org.jetbrains.intellij.build.dev.DevPluginInputOwnership
import org.jetbrains.intellij.build.dev.prepareDevPluginFromProjection
import java.nio.file.Path

fun main(args: Array<String>) {
  val options = parseCommandLineOptions(args)
  val projection = options.requiredPath("--projection")
  val catalogue = options.requiredPath("--catalogue")
  val descriptor = options.requiredPath("--descriptor")
  val pluginDirectory = options.requiredPath("--plugin-directory")
  val outputDirectory = Path.of(options.optional("--output-dir") ?: error("--output-dir is required")).normalize()
  val callbackPreparation = options.optional("--callback-preparation")?.let { value ->
    requireNotNull(value.toBooleanStrictOrNull()) { "--callback-preparation must be true or false" }
  }
  val executionVersion = options.optional("--execution-version")?.let { value ->
    requireNotNull(value.toIntOrNull()?.takeIf { it in 1..3 }) { "--execution-version must be 1, 2, or 3" }
  } ?: 1
  val ownership = DevPluginInputOwnership(
    callbackInputs = options.pathList("--callback-input"),
    remainderInputs = options.pathList("--remainder-input"),
  )
  options.checkNoUnknownOptions()
  prepareDevPluginFromProjection(
    projectionFile = projection,
    artifactCatalogueFile = catalogue,
    descriptorFile = descriptor,
    pluginDirectory = pluginDirectory,
    outputDirectory = outputDirectory.toAbsolutePath(),
    inputOwnership = ownership,
    catalogueOutputDirectory = outputDirectory,
    expectedExecutionVersion = executionVersion,
    expectedCallbackPreparation = callbackPreparation,
  )
}

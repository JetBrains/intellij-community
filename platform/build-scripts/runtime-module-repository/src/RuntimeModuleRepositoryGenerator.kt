// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.runtimeModuleRepository

import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.runtime.repository.RuntimePluginHeader
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.io.ZipFileWriter
import org.jetbrains.intellij.build.io.zipWriter
import org.jetbrains.jps.model.JpsProject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.zip.Deflater
import kotlin.io.path.createDirectories

private const val GENERATOR_VERSION: Int = 3
private const val BOOTSTRAP_MODULE_NAME: String = "intellij.platform.bootstrap"
private const val JAR_REPOSITORY_FILE_NAME: String = "module-descriptors.jar"
private const val COMPACT_REPOSITORY_FILE_NAME: String = "module-descriptors.dat"

/** The directory of the runtime module repository, relative to the distribution root. */
@ApiStatus.Internal
const val RUNTIME_REPOSITORY_MODULES_DIR_NAME: String = "modules"

@ApiStatus.Internal
const val MODULE_DESCRIPTORS_JAR_PATH: String = "$RUNTIME_REPOSITORY_MODULES_DIR_NAME/$JAR_REPOSITORY_FILE_NAME"

@ApiStatus.Internal
const val MODULE_DESCRIPTORS_COMPACT_PATH: String = "$RUNTIME_REPOSITORY_MODULES_DIR_NAME/$COMPACT_REPOSITORY_FILE_NAME"

/**
 * The module descriptors and the plugin headers of a runtime module repository.
 */
@ApiStatus.Internal
class RuntimeModuleRepositoryContent(
  @JvmField val descriptors: List<RawRuntimeModuleDescriptor>,
  @JvmField val pluginHeaders: List<RuntimePluginHeader>,
)

/**
 * Tells that the runtime module repository cannot be generated. [cause] is `null` if the generated repository is not valid.
 */
@ApiStatus.Internal
class RuntimeModuleRepositoryException(override val message: String, cause: Throwable?) : RuntimeException(message, cause)

/**
 * Computes the runtime module repository of a distribution.
 *
 * @param pluginDescriptorsData the core plugin, the bundled plugins and the additional frontend-only plugins, in this order
 * @param pluginConfigurationModuleToDistributionEntries maps the plugin descriptor module of each plugin to the files of the plugin
 * @param additionalFrontendOnlyPluginModules the plugin descriptor modules of the additional frontend-only plugins; the repository gets only their headers
 */
@ApiStatus.Internal
fun generateRuntimeModuleRepository(
  pluginDescriptorsData: List<PluginDescriptorDataForHeader>,
  pluginConfigurationModuleToDistributionEntries: Map<String, Collection<PluginDistributionEntry>>,
  additionalFrontendOnlyPluginModules: Set<String>,
  project: JpsProject,
): RuntimeModuleRepositoryContent {
  return generateRuntimeModuleRepository(
    pluginDescriptorsData, pluginConfigurationModuleToDistributionEntries, additionalFrontendOnlyPluginModules, JpsRuntimeModuleModel(project),
  )
}

/**
 * Computes the runtime module repository of a distribution from the facts of [model].
 */
@ApiStatus.Internal
fun generateRuntimeModuleRepository(
  pluginDescriptorsData: List<PluginDescriptorDataForHeader>,
  pluginConfigurationModuleToDistributionEntries: Map<String, Collection<PluginDistributionEntry>>,
  additionalFrontendOnlyPluginModules: Set<String>,
  model: RuntimeModuleModel,
): RuntimeModuleRepositoryContent {
  val pluginHeadersData = try {
    generateRuntimePluginHeaders(pluginDescriptorsData, pluginConfigurationModuleToDistributionEntries, model)
  }
  catch (e: Exception) {
    throw RuntimeModuleRepositoryException("Failed to generate runtime plugin headers: ${e.message}", e)
  }
  val pluginHeaders = pluginHeadersData.map { it.header }
  val pluginDataToGenerateModuleDescriptors = pluginHeadersData.filterNot { it.header.pluginDescriptorModuleId.name in additionalFrontendOnlyPluginModules }
  val distDescriptors = generateRuntimeModuleDescriptors(pluginDataToGenerateModuleDescriptors, model)
  val errors = ArrayList<String>()
  val errorReporter = object : RuntimeModuleRepositoryValidator.ErrorReporter {
    override fun reportError(errorMessage: String) {
      errors.add(errorMessage)
    }
  }
  RuntimeModuleRepositoryValidator.validate(distDescriptors, pluginHeaders, errorReporter)
  if (errors.isNotEmpty()) {
    throw RuntimeModuleRepositoryException(
      "Runtime module repository which is used to run the frontend process has ${errors.size} ${StringUtil.pluralize("error", errors.size)}:\n " +
      errors.joinToString("\n "),
      cause = null,
    )
  }
  return RuntimeModuleRepositoryContent(distDescriptors, pluginHeaders)
}

/**
 * Writes `module-descriptors.dat` and `module-descriptors.jar` to [targetDirectory], the `modules` directory of the distribution.
 */
@ApiStatus.Internal
fun saveRuntimeModuleRepository(repository: RuntimeModuleRepositoryContent, targetDirectory: Path) {
  try {
    targetDirectory.createDirectories()
    RuntimeModuleRepositorySerialization.saveToCompactFile(
      repository.descriptors, repository.pluginHeaders, BOOTSTRAP_MODULE_NAME, targetDirectory.resolve(COMPACT_REPOSITORY_FILE_NAME), GENERATOR_VERSION,
    )
    writeModuleDescriptorsJar(repository.descriptors, repository.pluginHeaders, BOOTSTRAP_MODULE_NAME, targetDirectory.resolve(JAR_REPOSITORY_FILE_NAME))
  }
  catch (e: IOException) {
    throw RuntimeException("Failed to save runtime module repository: ${e.message}", e)
  }
}

/**
 * Writes the JAR form of the repository. Entries carry no timestamp, so the same descriptors always give the same bytes.
 */
internal fun writeModuleDescriptorsJar(
  descriptors: List<RawRuntimeModuleDescriptor>,
  pluginHeaders: List<RuntimePluginHeader>,
  bootstrapModuleName: String?,
  jarFile: Path,
) {
  ZipFileWriter(
    zipWriter(targetFile = jarFile, packageIndexBuilder = null, overwrite = true),
    deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true),
  ).use { zipCreator ->
    RuntimeModuleRepositorySerialization.writeJarEntries(descriptors, pluginHeaders, bootstrapModuleName, GENERATOR_VERSION) { name, content ->
      zipCreator.compressedData(name, ByteBuffer.wrap(content))
    }
  }
}

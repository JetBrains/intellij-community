// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jpsBootstrap

import com.google.common.base.StandardSystemProperty
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.downloadFileToCacheLocation
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.extractFileToCacheLocation
import org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.verbose
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.*

object ClassesFromCompileInc {
  const val MANIFEST_JSON_URL_ENV_NAME = "JPS_BOOTSTRAP_MANIFEST_JSON_URL"

  @Throws(IOException::class, InterruptedException::class)
  fun downloadProjectClasses(project: JpsProject, communityRoot: BuildDependenciesCommunityRoot, modules: Collection<JpsModule?>?) {
    val manifestUrl = System.getenv(MANIFEST_JSON_URL_ENV_NAME)
    check(!(manifestUrl == null || manifestUrl.isBlank())) { "Env variable '$MANIFEST_JSON_URL_ENV_NAME' is missing or empty" }
    verbose("Got manifest json url '$manifestUrl' from $$MANIFEST_JSON_URL_ENV_NAME")
    val manifest = downloadFileToCacheLocation(communityRoot, URI.create(manifestUrl))
    val productionModuleOutputs = downloadProductionPartsFromMetadataJson(manifest, communityRoot, modules)
    assignModuleOutputs(project, productionModuleOutputs)
  }

  private fun assignModuleOutputs(project: JpsProject, productionModuleOutputs: Map<JpsModule, Path>) {
    val nonExistentPath = Path.of(
      System.getProperty(StandardSystemProperty.JAVA_IO_TMPDIR.key()),
      UUID.randomUUID().toString())

    // Set it to non-existent path since we won't run build and standard built output won't be available anyway
    val projectExtension = JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project)
    projectExtension.outputUrl = JpsPathUtil.pathToUrl(nonExistentPath.toString())
    for ((module, value) in productionModuleOutputs) {
      val javaExtension = JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(module)
      javaExtension.outputUrl = JpsPathUtil.pathToUrl(value.toString())
      javaExtension.isInheritOutput = false
    }
  }

  private fun downloadProductionPartsFromMetadataJson(metadataJson: Path, communityRoot: BuildDependenciesCommunityRoot, modules: Collection<JpsModule?>?): Map<JpsModule, Path> {
    var partsMetadata: CompilationPartsMetadata
    Files.newBufferedReader(metadataJson, StandardCharsets.UTF_8).use { manifestReader -> partsMetadata = Gson().fromJson(manifestReader, CompilationPartsMetadata::class.java) }
    check(partsMetadata.files!!.isNotEmpty()) { "partsMetadata.files is empty, check $metadataJson" }
    val tasks: MutableList<Callable<Pair<JpsModule, Path>>> = ArrayList()
    for (module in modules!!) {
      val c = Callable {
        val modulePrefix = "production/" + module!!.name
        val hash = partsMetadata.files!![modulePrefix]
          ?: throw IllegalStateException("Unable to find module output by name '$modulePrefix' in $metadataJson")
        val outputPartUri = URI.create(partsMetadata.serverUrl + "/" + partsMetadata.prefix + "/" + modulePrefix + "/" + hash + ".jar")
        val outputPart = downloadFileToCacheLocation(communityRoot, outputPartUri)
        val outputPartExtracted = extractFileToCacheLocation(communityRoot, outputPart)
        module to outputPartExtracted
      }
      tasks.add(c)
    }
    return JpsBootstrapUtil.executeTasksInParallel(tasks).associate { it.first to it.second }
  }

  private class CompilationPartsMetadata {
    @SerializedName("server-url")
    var serverUrl: String? = null
    var prefix: String? = null

    /**
     * Map compilation part path to a hash, for now SHA-256 is used.
     * sha256(file) == hash, though that may be changed in the future.
     */
    var files: Map<String, String>? = null
  }
}

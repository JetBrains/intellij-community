// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.io.URLUtil
import com.intellij.util.io.toByteArray
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.JpsCompilationData
import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import org.jetbrains.intellij.build.impl.moduleBased.OriginalModuleRepositoryImpl
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.moduleBased.OriginalModuleRepository
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.pathString

@ApiStatus.Internal
class BazelCompilationContext(
  private val delegate: CompilationContext,
) : CompilationContext {
  private data class ModuleOutputRoots(val productionJars: List<Path>, val testJars: List<Path>)

  private val modulesToOutputRoots: Map<String, ModuleOutputRoots> by lazy {
    BazelTargetsInfo.loadModulesOutputRootsFromBazelTargetsJson(delegate.paths.projectHome)
  }

  override val options: BuildOptions
    get() = delegate.options
  override val messages: BuildMessages
    get() = delegate.messages
  override val paths: BuildPaths
    get() = delegate.paths
  override val project: JpsProject
    get() = delegate.project
  override val projectModel: JpsModel
    get() = delegate.projectModel
  override val dependenciesProperties: DependenciesProperties
    get() = delegate.dependenciesProperties
  override val bundledRuntime: BundledRuntime
    get() = delegate.bundledRuntime
  override val compilationData: JpsCompilationData
    get() = delegate.compilationData
  override val stableJavaExecutable: Path
    get() = delegate.stableJavaExecutable

  override suspend fun getStableJdkHome(): Path = delegate.getStableJdkHome()

  override val classesOutputDirectory: Path
    get() = delegate.classesOutputDirectory

  override suspend fun getOriginalModuleRepository(): OriginalModuleRepository {
    generateRuntimeModuleRepository(this)
    return OriginalModuleRepositoryImpl(this)
  }

  override fun findRequiredModule(name: String): JpsModule = delegate.findRequiredModule(name)

  override fun findModule(name: String): JpsModule? = delegate.findModule(name)

  override suspend fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> {
    val moduleOutputRoots = modulesToOutputRoots[module.name] ?: error("No output roots for module '${module.name}'")
    return if (forTests) moduleOutputRoots.testJars else moduleOutputRoots.productionJars
  }

  override suspend fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean): List<String> {
    TODO()
  }

  override fun findFileInModuleSources(moduleName: String, relativePath: String, forTests: Boolean): Path? = delegate.findFileInModuleSources(moduleName, relativePath, forTests)

  override fun findFileInModuleSources(module: JpsModule, relativePath: String, forTests: Boolean): Path? = delegate.findFileInModuleSources(module, relativePath, forTests)

  override suspend fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    val result = getModuleOutputRoots(module, forTests).mapNotNull { moduleOutput ->
      var fileContent: ByteArray? = null
      readZipFile(moduleOutput) { name, data ->
        if (name == relativePath) {
          fileContent = data().toByteArray()
          ZipEntryProcessorResult.STOP
        }
        else {
          ZipEntryProcessorResult.CONTINUE
        }
      }
      return@mapNotNull fileContent
    }
    check(result.size < 2) {
      "More than one '$relativePath' file for module '${module.name}' in output roots"
    }
    return result.singleOrNull()
  }

  override fun notifyArtifactBuilt(artifactPath: Path): Unit = delegate.notifyArtifactBuilt(artifactPath)

  override fun createCopy(messages: BuildMessages, options: BuildOptions, paths: BuildPaths): CompilationContext {
    return BazelCompilationContext(delegate.createCopy(messages, options, paths)/*, modulesToOutputRoots*/)
  }

  override suspend fun prepareForBuild(): Unit = delegate.prepareForBuild()

  override suspend fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>?): Unit = delegate.compileModules(moduleNames, includingTestsInModules)

  override suspend fun withCompilationLock(block: suspend () -> Unit): Unit = delegate.withCompilationLock(block)

  private class BazelTargetsInfo {
    companion object {
      fun loadModulesOutputRootsFromBazelTargetsJson(projectRoot: Path): Map<String, ModuleOutputRoots> {
        val bazelTargetsJsonFile = projectRoot.resolve("build").resolve("bazel-targets.json")
        val targetsFile = bazelTargetsJsonFile.inputStream().use { Json.decodeFromStream<TargetsFile>(it) }

        val CONF = "$bazelOsArch-fastbuild"
        return targetsFile.modules.mapValues { (_, targetsFileModuleDescription) ->
          ModuleOutputRoots(
            productionJars = targetsFileModuleDescription.productionJars.map {
              projectRoot.resolve(it.replace("\${CONF}", CONF))
            },
            testJars = targetsFileModuleDescription.testJars.map {
              projectRoot.resolve(it.replace("\${CONF}", CONF))
            },
          )
        }
      }

      private val bazelOsArch = when (OS.CURRENT to CpuArch.CURRENT) {
        OS.Linux to CpuArch.X86_64 -> "k8"
        OS.Linux to CpuArch.ARM64 -> "aarch64"
        OS.Windows to CpuArch.X86_64 -> "x64_windows"
        OS.Windows to CpuArch.ARM64 -> "arm64_windows"
        OS.macOS to CpuArch.ARM64 -> "darwin_arm64"
        OS.macOS to CpuArch.X86_64 -> "darwin_x86_64"
        else -> error("Unsupported OS/Arch: ${OS.CURRENT} ${CpuArch.CURRENT}")
      }
    }

    @Serializable
    data class TargetsFileModuleDescription(
      val productionTargets: List<String>,
      val productionJars: List<String>,
      val testTargets: List<String>,
      val testJars: List<String>,
      val exports: List<String>,
    )

    @Serializable
    data class TargetsFile(
      val modules: Map<String, TargetsFileModuleDescription>,
      val projectLibraries: Map<String, String>,
    )
  }
}

@ApiStatus.Internal
fun isRunningFromBazelOut(): Boolean {
  val url = BazelCompilationContext::class.java.getResource("${BazelCompilationContext::class.java.simpleName}.class")
  if (url == null) {
    error("Unable to get '${BazelCompilationContext::class.java.simpleName}.class' file from resources")
  }

  return url.protocol == URLUtil.JAR_PROTOCOL && Path.of(URI.create(url.path)).any { it.pathString == "bazel-out" }
}
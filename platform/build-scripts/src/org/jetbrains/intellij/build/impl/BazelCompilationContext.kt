// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import com.intellij.util.io.URLUtil
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.JpsCompilationData
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.dependencies.DependenciesProperties
import org.jetbrains.intellij.build.impl.moduleBased.buildOriginalModuleRepository
import org.jetbrains.intellij.build.moduleBased.OriginalModuleRepository
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.pathString

@Internal
class BazelCompilationContext(
  private val delegate: CompilationContext,
  private val scope: CoroutineScope?,
) : CompilationContext {
  override val outputProvider: ModuleOutputProvider by lazy {
    BazelModuleOutputProvider(modules = delegate.project.modules, projectHome = delegate.paths.projectHome, bazelOutputRoot = bazelOutputRoot!!, scope = scope)
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

  private val originalModuleRepository = asyncLazy("Build original module repository") {
    buildOriginalModuleRepository(this@BazelCompilationContext)
  }

  override suspend fun getOriginalModuleRepository(): OriginalModuleRepository = originalModuleRepository.await()

  override suspend fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean): Collection<Path> {
    val enumerator = JpsJavaExtensionService.dependencies(module).recursively()
      .also {
        if (forTests) {
          it.withoutSdk()
        }
      }
      .includedIn(JpsJavaClasspathKind.runtime(forTests))

    val result = LinkedHashSet<Path>()
    enumerator.processModuleAndLibraries(
      { depModule ->
        result.addAll(outputProvider.getModuleOutputRoots(depModule, forTests = forTests))
        if (forTests) {  // incl. production
          result.addAll(outputProvider.getModuleOutputRoots(depModule, forTests = false))
        }
      },
      { library ->
        val moduleLibraryModuleName = (library.createReference().parentReference as? JpsModuleReference)?.moduleName
        for (path in outputProvider.findLibraryRoots(library.name, moduleLibraryModuleName)) {
          result.add(path)
        }
      }
    )
    return result
  }

  override fun findFileInModuleSources(moduleName: String, relativePath: String, forTests: Boolean): Path? = delegate.findFileInModuleSources(moduleName, relativePath, forTests)

  override fun findFileInModuleSources(module: JpsModule, relativePath: String, forTests: Boolean): Path? = delegate.findFileInModuleSources(module, relativePath, forTests)

  override fun notifyArtifactBuilt(artifactPath: Path): Unit = delegate.notifyArtifactBuilt(artifactPath)

  override fun createCopy(messages: BuildMessages, options: BuildOptions, paths: BuildPaths): CompilationContext {
    return BazelCompilationContext(delegate = delegate.createCopy(messages, options, paths), scope = scope)
  }

  override suspend fun prepareForBuild(): Unit = delegate.prepareForBuild()

  override suspend fun compileModules(moduleNames: Collection<String>?, includingTestsInModules: List<String>?) {
    // Be sure to call ./bazel-build-all.cmd
    // Later we will add all required Bazel dependencies to the build scripts target
  }

  override suspend fun withCompilationLock(block: suspend () -> Unit): Unit = delegate.withCompilationLock(block)

  fun replaceAllWithCompressedIfNeeded(files: List<Path>): List<Path> {
    val out = ArrayList<Path>(files.size)
    for (path in files) {
      if (!path.startsWith(classesOutputDirectory)) {
        out.add(path)
        continue
      }

      val module = outputProvider.findModule(path.name)
      if (module == null) {
        out.add(path)
        continue
      }

      val roots = outputProvider.getModuleOutputRoots(module, path.parent.name == "test")
      out.addAll(roots)
    }
    return out
  }
}

internal class BazelTargetsInfo {
  companion object {
    fun bazelTargetsJsonFile(projectHome: Path): Path = projectHome.resolve("build").resolve("bazel-targets.json")

    fun loadBazelTargetsJson(projectRoot: Path): TargetsFile {
      val targetsFile = bazelTargetsJsonFile(projectRoot).inputStream().use { Json.decodeFromStream<TargetsFile>(it) }
      return targetsFile
    }
  }

  @Serializable
  data class TargetsFileModuleDescription(
    val productionTargets: List<String>,
    val productionJars: List<String>,
    val testTargets: List<String>,
    val testJars: List<String>,
    val exports: List<String>,
    val moduleLibraries: Map<String, LibraryDescription>,
  )

  @Serializable
  data class LibraryDescription(
    val target: String,
    val jars: List<String>,
    val jarTargets: List<String>,
    val sourceJars: List<String>,
  )

  @Serializable
  data class TargetsFile(
    val modules: Map<String, TargetsFileModuleDescription>,
    val projectLibraries: Map<String, LibraryDescription>,
  )
}

@Internal
fun isRunningFromBazelOut(): Boolean = bazelOutputRoot != null

internal val bazelOutputRoot: Path? by lazy {
  val url = BazelCompilationContext::class.java.getResource("${BazelCompilationContext::class.java.simpleName}.class")
            ?: error("Unable to get '${BazelCompilationContext::class.java.simpleName}.class' file from resources")

  if (url.protocol != URLUtil.JAR_PROTOCOL) {
    return@lazy null
  }

  val path = Path.of(URI.create(url.path.substringBefore("!/")))

  if (path.none { it.pathString == "bazel-out" }) {
    // not running from bazel out
    return@lazy null
  }

  // resolving all symlinks should lead to the bazel output directory
  val realPath = path.toRealPath()
  val execRootIndex = realPath.indexOfFirst { it.pathString == "execroot" }
  if (execRootIndex <= 0) {
    error("Unable to find 'execroot' directory in the path: $realPath. class output: url=$url, path=$path")
  }

  val outputRoot = realPath.root.resolve(realPath.subpath(0, execRootIndex))
  Span.current().addEvent("Bazel output root: $outputRoot")
  return@lazy outputRoot
}

val CompilationContextImpl.asBazelIfNeeded: CompilationContext
  get() = toBazelIfNeeded(scope = null)

internal fun CompilationContextImpl.toBazelIfNeeded(scope: CoroutineScope?): CompilationContext {
  return when {
    isRunningFromBazelOut() -> BazelCompilationContext(delegate = this, scope = scope)
    else -> this
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.BuildLifetime
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.TestingOptions
import org.jetbrains.intellij.build.mapConcurrent
import org.jetbrains.intellij.build.impl.compilation.ArchivedCompilationOutputStorage
import org.jetbrains.intellij.build.impl.compilation.createArchivedStorage
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.writeLines

@Internal
class ArchivedCompilationContext internal constructor(
  private val delegate: CompilationContext,
  private val storage: ArchivedCompilationOutputStorage = createArchivedStorage(delegate),
  private val outputProviderLifetime: BuildLifetime?,
) : CompilationContext by delegate {
  val archivesLocation: Path
    get() = storage.archivedOutputDirectory

  override val outputProvider: ModuleOutputProvider = ArchivedModuleOutputProvider(delegateOutputProvider = delegate.outputProvider, storage = storage, lifetime = outputProviderLifetime)

  override fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean): List<Path> {
    return delegate.getModuleRuntimeClasspath(module, forTests).mapConcurrent { storage.getArchived(it) }.filterNotNull()
  }

  override fun createCopy(messages: BuildMessages, options: BuildOptions, paths: BuildPaths, lifetime: BuildLifetime?): CompilationContext {
    val effectiveLifetime = lifetime ?: outputProviderLifetime
    return ArchivedCompilationContext(delegate = delegate.createCopy(messages, options, paths, effectiveLifetime), storage = storage, outputProviderLifetime = effectiveLifetime)
  }

  fun saveMapping(file: Path) {
    file.writeLines(storage.getMapping().map { "${it.key.parent.fileName}/${it.key.fileName}=${it.value}" })
  }

  override fun toString(): String = "ArchivedCompilationContext(archivesLocation=$archivesLocation)"
}

private class ArchivedModuleOutputProvider(
  private val delegateOutputProvider: ModuleOutputProvider,
  private val storage: ArchivedCompilationOutputStorage,
  lifetime: BuildLifetime?,
) : ModuleOutputProvider by delegateOutputProvider {
  private val zipFilePool = ModuleOutputZipFilePool(lifetime)

  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> {
    val outputRoots = delegateOutputProvider.getModuleOutputRoots(module, forTests).mapNotNull { storage.getArchived(it) }
    for (outputRoot in outputRoots) {
      check(outputRoot.isRegularFile()) {
        "'${module.name}' module's output root doesn't exist: $outputRoot"
      }
    }
    return outputRoots
  }

  override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    for (moduleOutput in getModuleOutputRoots(module, forTests)) {
      if (!moduleOutput.startsWith(storage.archivedOutputDirectory)) {
        return delegateOutputProvider.readFileContentFromModuleOutput(module, relativePath, forTests)
      }
      zipFilePool.getData(moduleOutput, relativePath)?.let { return it }
    }
    return null
  }

  override fun findFileInAnyModuleOutput(relativePath: String, moduleNamePrefix: String?, processedModules: MutableSet<String>?): ByteArray? {
    for ((unarchivedPath, archivedPath) in storage.getMapping()) {
      val moduleName = unarchivedPath.fileName.toString()
      if (moduleNamePrefix != null && !moduleName.startsWith(moduleNamePrefix)) {
        continue
      }
      if (processedModules != null && !processedModules.add(moduleName)) {
        continue
      }
      zipFilePool.getData(archivedPath, relativePath)?.let {
        return it
      }
    }
    return null
  }

  override fun toString(): String {
    return "ArchivedModuleOutputProvider(" +
           "archivesLocation=${storage.archivedOutputDirectory}, " +
           "delegate.outputProvider=$delegateOutputProvider, " +
           "storage=$storage" +
           ")"
  }
}

val CompilationContext.asArchivedIfNeeded: CompilationContext
  get() = this.toArchivedIfNeeded(lifetime = null)

/**
 * Pass the [lifetime] that owns the read. It enables the zip cache of the module output pool, so a repeated
 * read of the same archive costs a map lookup instead of a new open.
 */
@Internal
fun CompilationContext.toArchivedIfNeeded(lifetime: BuildLifetime?): CompilationContext {
  return when {
    this is ArchivedCompilationContext -> this
    TestingOptions().useArchivedCompiledClasses || !System.getProperty("intellij.test.jars.mapping.file", "").isNullOrBlank() -> this.toArchivedContext(lifetime)
    else -> this
  }
}

val CompilationContext.asArchived: CompilationContext
  get() = toArchivedContext(lifetime = null)

internal fun CompilationContext.toArchivedContext(lifetime: BuildLifetime?): CompilationContext {
  return when (this) {
    is ArchivedCompilationContext -> this
    is BazelCompilationContext -> error("BazelCompilationContext must not be used as archived")
    is BuildContextImpl -> compilationContext.asArchived
    else -> ArchivedCompilationContext(delegate = this, outputProviderLifetime = lifetime)
  }
}

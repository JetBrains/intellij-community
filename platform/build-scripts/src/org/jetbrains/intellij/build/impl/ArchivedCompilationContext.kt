// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.TestingOptions
import org.jetbrains.intellij.build.impl.compilation.ArchivedCompilationOutputStorage
import org.jetbrains.intellij.build.impl.compilation.createArchivedStorage
import org.jetbrains.intellij.build.impl.moduleBased.buildOriginalModuleRepository
import org.jetbrains.intellij.build.moduleBased.OriginalModuleRepository
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.writeLines

@Internal
class ArchivedCompilationContext internal constructor(
  private val delegate: CompilationContext,
  private val storage: ArchivedCompilationOutputStorage = createArchivedStorage(delegate),
  private val outputProviderScope: CoroutineScope?,
) : CompilationContext by delegate {
  val archivesLocation: Path
    get() = storage.archivedOutputDirectory

  private val originalModuleRepository = suspendingLazy("Build original module repository") {
    buildOriginalModuleRepository(this@ArchivedCompilationContext)
  }

  override val outputProvider: ModuleOutputProvider = ArchivedModuleOutputProvider(delegateOutputProvider = delegate.outputProvider, storage = storage, scope = outputProviderScope)

  override suspend fun getOriginalModuleRepository(): OriginalModuleRepository = originalModuleRepository.await()

  override suspend fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean): List<Path> {
    return coroutineScope {
      delegate.getModuleRuntimeClasspath(module, forTests).map { async { storage.getArchived(it) } }.awaitAll().filterNotNull()
    }
  }

  override fun createCopy(messages: BuildMessages, options: BuildOptions, paths: BuildPaths, scope: CoroutineScope?): CompilationContext {
    val effectiveScope = scope ?: outputProviderScope
    return ArchivedCompilationContext(delegate = delegate.createCopy(messages, options, paths, effectiveScope), storage = storage, outputProviderScope = effectiveScope)
  }

  fun saveMapping(file: Path) {
    file.writeLines(storage.getMapping().map { "${it.key.parent.fileName}/${it.key.fileName}=${it.value}" })
  }

  override fun toString(): String = "ArchivedCompilationContext(archivesLocation=$archivesLocation)"
}

private class ArchivedModuleOutputProvider(
  private val delegateOutputProvider: ModuleOutputProvider,
  private val storage: ArchivedCompilationOutputStorage,
  scope: CoroutineScope?,
) : ModuleOutputProvider by delegateOutputProvider {
  private val zipFilePool = ModuleOutputZipFilePool(scope)

  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> {
    val outputRoots = delegateOutputProvider.getModuleOutputRoots(module, forTests).mapNotNull { storage.getArchived(it) }
    for (outputRoot in outputRoots) {
      check(outputRoot.isRegularFile()) {
        "'${module.name}' module's output root doesn't exist: $outputRoot"
      }
    }
    return outputRoots
  }

  override suspend fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    for (moduleOutput in getModuleOutputRoots(module, forTests)) {
      if (!moduleOutput.startsWith(storage.archivedOutputDirectory)) {
        return delegateOutputProvider.readFileContentFromModuleOutput(module, relativePath, forTests)
      }
      zipFilePool.getData(moduleOutput, relativePath)?.let { return it }
    }
    return null
  }

  override suspend fun findFileInAnyModuleOutput(relativePath: String, moduleNamePrefix: String?, processedModules: MutableSet<String>?): ByteArray? {
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

  override fun getProjectLibraryToModuleMap(): Map<String, String> {
    return delegateOutputProvider.getProjectLibraryToModuleMap()
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
  get() = this.toArchivedIfNeeded(scope = null)

@Experimental
internal fun CompilationContext.toArchivedIfNeeded(scope: CoroutineScope?): CompilationContext {
  return when {
    this is ArchivedCompilationContext -> this
    TestingOptions().useArchivedCompiledClasses || !System.getProperty("intellij.test.jars.mapping.file", "").isNullOrBlank() -> this.toArchivedContext(scope)
    else -> this
  }
}

val CompilationContext.asArchived: CompilationContext
  get() = toArchivedContext(scope = null)

internal fun CompilationContext.toArchivedContext(scope: CoroutineScope?): CompilationContext {
  return when (this) {
    is ArchivedCompilationContext -> this
    is BazelCompilationContext -> error("BazelCompilationContext must not be used as archived")
    is BuildContextImpl -> compilationContext.asArchived
    else -> ArchivedCompilationContext(delegate = this, outputProviderScope = scope)
  }
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import kotlin.io.path.writeLines

@Internal
class ArchivedCompilationContext internal constructor(
  private val delegate: CompilationContext,
  private val storage: ArchivedCompilationOutputStorage = createArchivedStorage(delegate),
  scope: CoroutineScope?,
) : CompilationContext by delegate {
  val archivesLocation: Path
    get() = storage.archivedOutputDirectory

  private val originalModuleRepository = asyncLazy("Build original module repository") {
    buildOriginalModuleRepository(this@ArchivedCompilationContext)
  }

  override val outputProvider: ModuleOutputProvider = ArchivedModuleOutputProvider(delegateOutputProvider = delegate.outputProvider, storage = storage, scope = scope)

  override suspend fun getOriginalModuleRepository(): OriginalModuleRepository = originalModuleRepository.await()

  override suspend fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean): List<Path> {
    return doReplace(delegate.getModuleRuntimeClasspath(module, forTests))
  }

  override fun createCopy(messages: BuildMessages, options: BuildOptions, paths: BuildPaths): CompilationContext {
    return ArchivedCompilationContext(delegate = delegate.createCopy(messages, options, paths), storage = storage, scope = null)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  fun replaceWithCompressedIfNeeded(p: Path): Path = storage.getArchived(p)

  suspend fun replaceAllWithCompressedIfNeeded(files: List<Path>): List<Path> = doReplace(files)

  private suspend fun doReplace(files: Collection<Path>): List<Path> {
    return coroutineScope {
      files.map { file ->
        async {
          replaceWithCompressedIfNeeded(file)
        }
      }.awaitAll()
    }
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
    return delegateOutputProvider.getModuleOutputRoots(module, forTests).map { storage.getArchived(it) }
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

private fun CompilationContext.toArchivedContext(scope: CoroutineScope?): CompilationContext {
  return when (this) {
    is ArchivedCompilationContext -> this
    is BazelCompilationContext -> error("BazelCompilationContext must not be used as archived")
    is BuildContextImpl -> compilationContext.asArchived
    else -> ArchivedCompilationContext(delegate = this, scope = scope)
  }
}

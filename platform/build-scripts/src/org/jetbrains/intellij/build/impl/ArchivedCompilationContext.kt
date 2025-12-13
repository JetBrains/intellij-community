// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.io.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
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
import org.jetbrains.intellij.build.impl.moduleBased.buildOriginalModuleRepository
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.moduleBased.OriginalModuleRepository
import org.jetbrains.jps.model.module.JpsModule
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.writeLines

@Internal
class ArchivedCompilationContext internal constructor(
  private val delegate: CompilationContext,
  private val storage: ArchivedCompilationOutputStorage = ArchivedCompilationOutputStorage(
    paths = delegate.paths,
    classesOutputDirectory = delegate.classesOutputDirectory,
    messages = delegate.messages
  ).apply {
    delegate.options.pathToCompiledClassesArchivesMetadata?.let {
      this.loadMetadataFile(it)
    }
    System.getProperty("intellij.test.jars.mapping.file")?.let {
      this.loadMapping(Path.of(it))
    }
    if (getMapping().isNotEmpty()) {
      delegate.messages.info("Loading archived compilation mappings: " + getMapping())
    }
  },
  scope: CoroutineScope?,
) : CompilationContext by delegate {
  val archivesLocation: Path
    get() = storage.archivedOutputDirectory

  private val originalModuleRepository = asyncLazy("Build original module repository") {
    buildOriginalModuleRepository(this@ArchivedCompilationContext)
  }

  override val outputProvider: ModuleOutputProvider = ArchivedModuleOutputProvider(delegateOutputProvider = delegate.outputProvider, context = this, scope = scope)

  override suspend fun getOriginalModuleRepository(): OriginalModuleRepository = originalModuleRepository.await()

  override suspend fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean): List<Path> {
    return doReplace(delegate.getModuleRuntimeClasspath(module, forTests), inputMapper = { it }, resultMapper = { it })
  }

  override fun createCopy(messages: BuildMessages, options: BuildOptions, paths: BuildPaths): CompilationContext {
    return ArchivedCompilationContext(delegate = delegate.createCopy(messages, options, paths), storage = storage, scope = null)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  fun replaceWithCompressedIfNeeded(p: Path): Path = storage.getArchived(p)

  suspend fun replaceWithCompressedIfNeededLP(files: List<Path>): List<Path> {
    return doReplace(files, inputMapper = { it }, resultMapper = { it })
  }

  suspend fun replaceWithCompressedIfNeededLF(files: List<Path>): List<Path> {
    return doReplace(files, inputMapper = { it }, resultMapper = { it })
  }

  private suspend inline fun <I : Any, R : Any> doReplace(
    files: Collection<I>,
    crossinline inputMapper: (I) -> Path,
    crossinline resultMapper: (Path) -> R,
  ): List<R> {
    return coroutineScope {
      files.map { file ->
        async {
          resultMapper(replaceWithCompressedIfNeeded(inputMapper(file)))
        }
      }
    }.map { it.getCompleted() }
  }

  fun saveMapping(file: Path) {
    file.writeLines(storage.getMapping().map { "${it.key.parent.fileName}/${it.key.fileName}=${it.value}" })
  }
}

private class ArchivedModuleOutputProvider(
  private val delegateOutputProvider: ModuleOutputProvider,
  private val context: ArchivedCompilationContext,
  scope: CoroutineScope?,
) : ModuleOutputProvider by delegateOutputProvider {
  private val zipFilePool = ModuleOutputZipFilePool(scope)

  override fun getModuleOutputRoots(module: JpsModule, forTests: Boolean): List<Path> {
    return delegateOutputProvider.getModuleOutputRoots(module, forTests).map { context.replaceWithCompressedIfNeeded(it) }
  }

  override suspend fun readFileContentFromModuleOutputAsync(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    for (moduleOutput in getModuleOutputRoots(module, forTests)) {
      if (!moduleOutput.startsWith(context.archivesLocation)) {
        return delegateOutputProvider.readFileContentFromModuleOutputAsync(module, relativePath, forTests)
      }
      zipFilePool.getZipFile(moduleOutput)?.getData(relativePath)?.let { return it }
    }
    return null
  }

  override fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String, forTests: Boolean): ByteArray? {
    val result = getModuleOutputRoots(module, forTests).mapNotNull { moduleOutput ->
      if (!moduleOutput.startsWith(context.archivesLocation)) {
        return delegateOutputProvider.readFileContentFromModuleOutput(module, relativePath)
      }

      var fileContent: ByteArray? = null
      try {
        readZipFile(moduleOutput) { name, data ->
          if (name == relativePath) {
            fileContent = data().toByteArray()
            ZipEntryProcessorResult.STOP
          }
          else {
            ZipEntryProcessorResult.CONTINUE
          }
        }
      }
      catch (e: IOException) {
        // If the zip file doesn't exist, return null and let other output roots be tried
        if (generateSequence<Throwable>(e) { it.cause }.any { it is NoSuchFileException }) {
          return@mapNotNull null
        }
        // re-throw unexpected I/O errors (corrupted zip, permissions, etc.)
        throw e
      }
      return@mapNotNull fileContent
    }
    check(result.size < 2) {
      "More than one '$relativePath' file for module '${module.name}' in output roots"
    }
    return result.singleOrNull()
  }

  override fun toString(): String {
    return "ArchivedModuleOutputProvider(" +
           "archivesLocation=${context.archivesLocation}, " +
           "delegate.outputProvider=$delegateOutputProvider, " +
           "context=$context" +
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

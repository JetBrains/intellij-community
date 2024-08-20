// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.compilation.ArchivedCompilationOutputsStorage
import org.jetbrains.jps.model.module.JpsModule
import java.io.File
import java.nio.file.Path
import kotlin.io.path.writeLines

@ApiStatus.Internal
class ArchivedCompilationContext(
  private val delegate: CompilationContext,
  private val storage: ArchivedCompilationOutputsStorage = ArchivedCompilationOutputsStorage(paths = delegate.paths, classesOutputDirectory = delegate.classesOutputDirectory).apply {
    delegate.options.pathToCompiledClassesArchivesMetadata?.let {
      this.loadMetadataFile(Path.of(it))
    }
  },
) : CompilationContext by delegate {
  val archivesLocation: Path
    get() = storage.archivedOutputDirectory

  override suspend fun getModuleOutputDir(module: JpsModule, forTests: Boolean): Path {
    return replaceWithCompressedIfNeeded(delegate.getModuleOutputDir(module = module, forTests = forTests))
  }

  override fun getModuleTestsOutputDir(module: JpsModule): Path {
    return runBlocking(Dispatchers.IO) {
      replaceWithCompressedIfNeeded(delegate.getModuleTestsOutputDir(module))
    }
  }

  @Deprecated("Use getModuleTestsOutputDir instead", replaceWith = ReplaceWith("getModuleTestsOutputDir(module)"))
  override fun getModuleTestsOutputPath(module: JpsModule): String {
    @Suppress("DEPRECATION")
    return replaceWithCompressedIfNeeded(delegate.getModuleTestsOutputPath(module))
  }

  override fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean): List<String> {
    return doReplace(delegate.getModuleRuntimeClasspath(module, forTests), inputMapper = { Path.of(it) }, resultMapper = { it.toString() })
  }

  override fun createCopy(messages: BuildMessages, options: BuildOptions, paths: BuildPaths): CompilationContext {
    return ArchivedCompilationContext(delegate.createCopy(messages, options, paths), storage)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  fun replaceWithCompressedIfNeeded(p: String): String {
    return runBlocking(Dispatchers.IO) {
      storage.getArchived(Path.of(p)).toString()
    }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  suspend fun replaceWithCompressedIfNeeded(p: Path): Path = storage.getArchived(p)

  fun replaceWithCompressedIfNeededLP(files: List<Path>): List<Path> {
    return doReplace(files, inputMapper = { it }, resultMapper = { it })
  }

  fun replaceWithCompressedIfNeededLF(files: List<File>): List<File> {
    return doReplace(files, inputMapper = { it.toPath() }, resultMapper = { it.toFile() })
  }

  private inline fun <I : Any, R : Any> doReplace(
    files: List<I>,
    crossinline inputMapper: (I) -> Path,
    crossinline resultMapper: (Path) -> R,
  ): List<R> {
    return runBlocking(Dispatchers.IO) {
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

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.util.io.toByteArray
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.compilation.ArchivedCompilationOutputStorage
import org.jetbrains.intellij.build.impl.moduleBased.OriginalModuleRepositoryImpl
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.moduleBased.OriginalModuleRepository
import org.jetbrains.jps.model.module.JpsModule
import java.io.File
import java.nio.file.Path
import kotlin.io.path.writeLines

@ApiStatus.Internal
class ArchivedCompilationContext(
  private val delegate: CompilationContext,
  private val storage: ArchivedCompilationOutputStorage = ArchivedCompilationOutputStorage(paths = delegate.paths, classesOutputDirectory = delegate.classesOutputDirectory).apply {
    delegate.options.pathToCompiledClassesArchivesMetadata?.let {
      this.loadMetadataFile(it)
    }
  },
) : CompilationContext by delegate {
  val archivesLocation: Path
    get() = storage.archivedOutputDirectory

  override suspend fun getOriginalModuleRepository(): OriginalModuleRepository {
    generateRuntimeModuleRepository(this)
    return OriginalModuleRepositoryImpl(this)
  }

  override suspend fun getModuleOutputDir(module: JpsModule, forTests: Boolean): Path {
    return replaceWithCompressedIfNeeded(delegate.getModuleOutputDir(module = module, forTests = forTests))
  }

  override suspend fun getModuleTestsOutputDir(module: JpsModule): Path {
    return replaceWithCompressedIfNeeded(delegate.getModuleTestsOutputDir(module))
  }

  override suspend fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean): List<String> {
    return doReplace(delegate.getModuleRuntimeClasspath(module, forTests), inputMapper = { Path.of(it) }, resultMapper = { it.toString() })
  }

  override suspend fun readFileContentFromModuleOutput(module: JpsModule, relativePath: String): ByteArray? {
    val moduleOutput = getModuleOutputDir(module)
    if (!moduleOutput.startsWith(archivesLocation)) {
      return delegate.readFileContentFromModuleOutput(module, relativePath)
    }

    var fileContent: ByteArray? = null
    readZipFile(moduleOutput) { name, data ->
      if (name == relativePath) {
        fileContent = data().toByteArray()
      }
    }
    return fileContent
  }

  override fun createCopy(messages: BuildMessages, options: BuildOptions, paths: BuildPaths): CompilationContext {
    return ArchivedCompilationContext(delegate.createCopy(messages, options, paths), storage)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  suspend fun replaceWithCompressedIfNeeded(p: Path): Path = storage.getArchived(p)

  suspend fun replaceWithCompressedIfNeededLP(files: List<Path>): List<Path> {
    return doReplace(files, inputMapper = { it }, resultMapper = { it })
  }

  suspend fun replaceWithCompressedIfNeededLF(files: List<File>): List<File> {
    return doReplace(files, inputMapper = { it.toPath() }, resultMapper = { it.toFile() })
  }

  private suspend inline fun <I : Any, R : Any> doReplace(
    files: List<I>,
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

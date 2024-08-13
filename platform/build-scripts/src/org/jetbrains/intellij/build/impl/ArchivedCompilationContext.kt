// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.tool.mapConcurrently
import kotlinx.coroutines.Dispatchers
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
  }
) : CompilationContext by delegate {
  val archivesLocation: Path
    get() = storage.archivedOutputDirectory

  override fun getModuleOutputDir(module: JpsModule, forTests: Boolean): Path {
    return replaceWithCompressedIfNeeded(delegate.getModuleOutputDir(module = module, forTests = forTests))
  }

  override fun getModuleTestsOutputDir(module: JpsModule): Path {
    return replaceWithCompressedIfNeeded(delegate.getModuleTestsOutputDir(module))
  }

  @Deprecated("Use getModuleTestsOutputDir instead", replaceWith = ReplaceWith("getModuleTestsOutputDir(module)"))
  override fun getModuleTestsOutputPath(module: JpsModule): String {
    @Suppress("DEPRECATION")
    return replaceWithCompressedIfNeeded(delegate.getModuleTestsOutputPath(module))
  }

  override fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean): List<String> {
    return replaceWithCompressedIfNeededLS(delegate.getModuleRuntimeClasspath(module, forTests))
  }

  override fun createCopy(messages: BuildMessages, options: BuildOptions, paths: BuildPaths): CompilationContext {
    return ArchivedCompilationContext(delegate.createCopy(messages, options, paths), storage)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  fun replaceWithCompressedIfNeeded(p: String): String {
    return storage.getArchived(Path.of(p)).toString()
  }

  @Suppress("MemberVisibilityCanBePrivate")
  fun replaceWithCompressedIfNeeded(p: Path): Path {
    return storage.getArchived(p)
  }

  @Suppress("MemberVisibilityCanBePrivate")
  fun replaceWithCompressedIfNeeded(f: File): File {
    return storage.getArchived(f.toPath()).toFile()
  }

  fun replaceWithCompressedIfNeededLS(paths: List<String>): List<String> {
    return runBlocking(Dispatchers.IO) { paths.mapConcurrently(100, ::replaceWithCompressedIfNeeded) }
  }

  fun replaceWithCompressedIfNeededLP(paths: List<Path>): List<Path> {
    return runBlocking(Dispatchers.IO) { paths.mapConcurrently(100, ::replaceWithCompressedIfNeeded) }
  }

  fun replaceWithCompressedIfNeededLF(paths: List<File>): List<File> {
    return runBlocking(Dispatchers.IO) { paths.mapConcurrently(100, ::replaceWithCompressedIfNeeded) }
  }

  fun saveMapping(file: Path) {
    file.writeLines(storage.getMapping().map { "${it.key.parent.fileName}/${it.key.fileName}=${it.value}" })
  }
}

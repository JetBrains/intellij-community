// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.tool.mapConcurrently
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.compilation.ArchivedCompilationOutputsStorage
import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path
import kotlin.io.path.writeLines

class ArchivedCompilationContext(private val delegate: CompilationContext) : CompilationContext by delegate {
  private val storage = ArchivedCompilationOutputsStorage(paths = paths, classesOutputDirectory = classesOutputDirectory).apply {
    options.pathToCompiledClassesArchivesMetadata?.let {
      this.loadMetadataFile(Path.of(it))
    }
  }

  val archivesLocation get() = storage.archivedOutputDirectory

  override fun getModuleOutputDir(module: JpsModule): Path {
    return replaceWithCompressedIfNeeded(delegate.getModuleOutputDir(module))
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

  @Suppress("MemberVisibilityCanBePrivate")
  fun replaceWithCompressedIfNeeded(p: String): String {
    return storage.getArchived(Path.of(p)).toString()
  }

  @Suppress("MemberVisibilityCanBePrivate")
  fun replaceWithCompressedIfNeeded(p: Path): Path {
    return storage.getArchived(p)
  }

  fun replaceWithCompressedIfNeededLS(paths: List<String>): List<String> {
    return runBlocking(Dispatchers.IO) { paths.mapConcurrently(100, ::replaceWithCompressedIfNeeded) }
  }

  fun replaceWithCompressedIfNeededLP(paths: List<Path>): List<Path> {
    return runBlocking(Dispatchers.IO) { paths.mapConcurrently(100, ::replaceWithCompressedIfNeeded) }
  }

  fun saveMapping(file: Path) {
    file.writeLines(storage.getMapping().map { "${it.key.parent.fileName}/${it.key.fileName}=${it.value}" })
  }
}

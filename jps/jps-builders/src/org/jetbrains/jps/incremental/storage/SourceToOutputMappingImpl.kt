// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.builders.storage.SourceToOutputMapping
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService
import java.io.IOException
import java.nio.file.Path

@ApiStatus.Internal
class SourceToOutputMappingImpl @Throws(IOException::class) constructor(
  storePath: Path,
  relativizer: PathRelativizerService,
) : SourceToOutputMapping, StorageOwner {
  private val mapping = OneToManyPathsMapping(storePath, relativizer)

  override fun setOutputs(srcPath: String, outputs: List<String>) {
    mapping.setOutputs(srcPath, outputs)
  }

  override fun setOutput(srcPath: String, outputPath: String) {
    mapping.setOutput(srcPath, outputPath)
  }

  override fun appendOutput(srcPath: String, outputPath: String) {
    mapping.appendData(srcPath, outputPath)
  }

  @Throws(IOException::class)
  override fun remove(srcPath: String) {
    mapping.remove(srcPath)
  }

  @Throws(IOException::class)
  override fun removeOutput(srcPath: String, outputPath: String) {
    mapping.removeData(srcPath, outputPath)
  }

  @Throws(IOException::class)
  override fun getOutputs(srcPath: String): List<String>? = mapping.getOutputs(srcPath)

  @Throws(IOException::class)
  override fun getOutputs(sourceFile: Path): Collection<Path>? = mapping.getOutputs(sourceFile)

  @Throws(IOException::class)
  override fun getSourcesIterator(): Iterator<String> = mapping.keysIterator

  @Throws(IOException::class)
  override fun getSourceFileIterator(): Iterator<Path> {
    return mapping.keysIterator.asSequence().map { Path.of(it) }.iterator()
  }

  @Throws(IOException::class)
  override fun cursor(): SourceToOutputMappingCursor {
    return mapping.cursor()
  }

  override fun flush(memoryCachesOnly: Boolean) {
    mapping.flush(memoryCachesOnly)
  }

  override fun close() {
    mapping.close()
  }

  override fun clean() {
    mapping.clean()
  }
}

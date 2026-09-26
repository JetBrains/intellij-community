// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

@Internal
fun BuildOptions.normalizeCompiledClassesOptions(defaultClassesOutputDirectory: Path): BuildOptions {
  val reuseProjectOutput = useCompiledClassesFromProjectOutput
  return copy(
    useCompiledClassesFromProjectOutput = reuseProjectOutput,
    pathToCompiledClassesArchivesMetadata = pathToCompiledClassesArchivesMetadata?.takeIf { !reuseProjectOutput },
    pathToCompiledClassesArchive = pathToCompiledClassesArchive?.takeIf { !reuseProjectOutput },
    unpackCompiledClassesArchives = unpackCompiledClassesArchives.takeIf { !reuseProjectOutput } ?: true,
    classOutDir = (classOutDir?.let { Path.of(it) } ?: defaultClassesOutputDirectory).toString(),
  )
}

@Internal
fun getProductionClassesOutputDirectory(classesOutputDirectory: Path): Path = classesOutputDirectory.resolve("production")

@Internal
fun getTestClassesOutputDirectory(classesOutputDirectory: Path): Path = classesOutputDirectory.resolve("test")

val CompilationContext.productionClassesOutputDirectory: Path
  get() = getProductionClassesOutputDirectory(classesOutputDirectory)

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path

@Internal
class JpsCompilationData(
  @JvmField val dataStorageRoot: Path,
  private val classesOutputDirectory: Path,
  @JvmField val buildLogFile: Path,
  categoriesWithDebugLevelNullable: String?,
) {
  @JvmField
  val compiledModules: MutableSet<String> = LinkedHashSet()
  @JvmField
  val compiledModuleTests: MutableSet<String> = LinkedHashSet()
  @JvmField
  val builtArtifacts: MutableSet<String> = LinkedHashSet()
  @JvmField
  var statisticsReported: Boolean = false
  @JvmField
  var projectDependenciesResolved: Boolean = false
  @JvmField
  var runtimeModuleRepositoryGenerated: Boolean = false

  @JvmField
  val categoriesWithDebugLevel: String = categoriesWithDebugLevelNullable ?: ""

  internal fun isIncrementalCompilationDataAvailable(): Boolean {
    val productionClasses = classesOutputDirectory.resolve("production")
    return isNotEmptyDir(dataStorageRoot) && isNotEmptyDir(productionClasses)
  }

  internal fun reset() {
    compiledModules.clear()
    compiledModuleTests.clear()
    statisticsReported = false
  }
}

private fun isNotEmptyDir(file: Path): Boolean {
  try {
    return Files.newDirectoryStream(file).use { it.any() }
  }
  catch (ignore: NoSuchFileException) {
    return false
  }
  catch (ignore: NotDirectoryException) {
    return false
  }
}
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

class JpsCompilationData(val dataStorageRoot: Path, val buildLogFile: Path, categoriesWithDebugLevelNullable: String?) {
  val compiledModules: MutableSet<String> = LinkedHashSet()
  val compiledModuleTests: MutableSet<String> = LinkedHashSet()
  val builtArtifacts: MutableSet<String> = LinkedHashSet()
  var statisticsReported: Boolean = false
  var projectDependenciesResolved: Boolean = false
  var runtimeModuleRepositoryGenerated: Boolean = false

  val categoriesWithDebugLevel: String = categoriesWithDebugLevelNullable ?: ""
  val dataStorageRootListing: List<Path>
    get() = if (dataStorageRoot.exists() && dataStorageRoot.isDirectory()) {
      dataStorageRoot.listDirectoryEntries()
    }
    else emptyList()

  fun reset() {
    compiledModules.clear()
    compiledModuleTests.clear()
    statisticsReported = false
  }
}

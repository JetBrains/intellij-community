// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex
import java.nio.file.Path

class JpsCompilationData(val dataStorageRoot: Path, val buildLogFile: Path, categoriesWithDebugLevelNullable: String?) {
  val compiledModules: MutableSet<String> = LinkedHashSet()
  val compiledModuleTests: MutableSet<String> = LinkedHashSet()
  val builtArtifacts: MutableSet<String> = LinkedHashSet()
  var statisticsReported: Boolean = false
  var projectDependenciesResolved: Boolean = false
  var runtimeModuleRepositoryGenerated: Boolean = false

  val categoriesWithDebugLevel: String = categoriesWithDebugLevelNullable ?: ""
  internal fun isIncrementalCompilationDataAvailable(): Boolean {
    return CompilerReferenceIndex.exists(dataStorageRoot.toFile())
  }

  fun reset() {
    compiledModules.clear()
    compiledModuleTests.clear()
    statisticsReported = false
  }
}

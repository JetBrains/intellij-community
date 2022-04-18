// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic
import org.jetbrains.annotations.Nullable

@CompileStatic
final class JpsCompilationData {
  final File dataStorageRoot
  final Set<String> compiledModules = new HashSet<>()
  final Set<String> compiledModuleTests = new HashSet<>()
  final Set<String> builtArtifacts = new HashSet<>()
  boolean compiledClassesAreLoaded
  boolean statisticsReported
  boolean projectDependenciesResolved
  final File buildLogFile
  final String categoriesWithDebugLevel

  JpsCompilationData(File dataStorageRoot, File buildLogFile, @Nullable String categoriesWithDebugLevel) {
    this.buildLogFile = buildLogFile
    this.dataStorageRoot = dataStorageRoot
    this.categoriesWithDebugLevel = categoriesWithDebugLevel ?: ""
  }
}

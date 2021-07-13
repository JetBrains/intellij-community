// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.



package org.jetbrains.intellij.build.impl

import com.intellij.openapi.diagnostic.Logger
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.jps.gant.Log4jFileLoggerFactory

@CompileStatic
final class JpsCompilationData {
  final File dataStorageRoot
  final Set<String> compiledModules = new HashSet<>()
  final Set<String> compiledModuleTests = new HashSet<>()
  final Set<String> builtArtifacts = new HashSet<>()
  Logger.Factory fileLoggerFactory
  boolean statisticsReported
  boolean projectDependenciesResolved

  JpsCompilationData(File dataStorageRoot, File buildLogFile, String categoriesWithDebugLevel, BuildMessages messages) {
    this.dataStorageRoot = dataStorageRoot
    categoriesWithDebugLevel = categoriesWithDebugLevel ?: ""
    try {
      fileLoggerFactory = new Log4jFileLoggerFactory(buildLogFile, categoriesWithDebugLevel)
      messages.info("Build log (${!categoriesWithDebugLevel.isEmpty() ? "debug level for $categoriesWithDebugLevel" : "info"}) will be written to $buildLogFile.absolutePath")
    }
    catch (Throwable t) {
      messages.warning("Cannot setup additional logging to $buildLogFile.absolutePath: $t.message")
    }
  }
}

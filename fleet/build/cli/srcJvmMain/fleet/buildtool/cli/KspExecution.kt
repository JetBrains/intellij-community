// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.buildtool.cli

import com.google.devtools.ksp.impl.KotlinSymbolProcessing
import com.google.devtools.ksp.processing.KSPConfig
import com.google.devtools.ksp.processing.KspGradleLogger
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.ServiceLoader

internal fun executeKsp(config: KSPConfig, processorClasspath: List<Path>): Int {
  val loggingLevel = when (System.getProperty("ksp.logging", "warn").lowercase()) {
    "error" -> KspGradleLogger.LOGGING_LEVEL_ERROR
    "warn", "warning" -> KspGradleLogger.LOGGING_LEVEL_WARN
    "info" -> KspGradleLogger.LOGGING_LEVEL_INFO
    "debug" -> KspGradleLogger.LOGGING_LEVEL_LOGGING
    else -> KspGradleLogger.LOGGING_LEVEL_WARN
  }
  val logger = KspGradleLogger(loggingLevel)
  return URLClassLoader(processorClasspath.map { it.toUri().toURL() }.toTypedArray(), object {}::class.java.classLoader)
    .use { processorClassloader ->
      val processorProviders = ServiceLoader.load(SymbolProcessorProvider::class.java, processorClassloader).toList()
      KotlinSymbolProcessing(config, processorProviders, logger).execute()
    }.code
}

internal fun findSourceRoot(source: Path): Path {
  var current: Path? = source.parent
  while (current != null) {
    val directoryName = current.fileName?.toString()
    // `srcCommonMain`, `srcWasmJsMain`, ... plus the checked-in generated flavors (`genCommonMain`, ...).
    if (directoryName != null && (directoryName.startsWith("src") || directoryName.startsWith("gen"))) {
      return current
    }
    current = current.parent
  }
  return source.parent ?: source
}

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging.jps;

import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.JulLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.Logger.Factory
import com.intellij.openapi.diagnostic.RollingFileHandler
import org.jetbrains.annotations.ApiStatus

import java.nio.file.Path
import java.util.logging.Level

@ApiStatus.Internal
class JpsFileLoggerFactory(logFile: Path, categoriesWithDebugLevel: String) : Factory {
  private val appender = RollingFileHandler(logFile, 20_000_000L, 10, true)
  private val categoriesWithDebugLevel = if (categoriesWithDebugLevel.isEmpty()) emptyList() else categoriesWithDebugLevel.split(',')

  init {
    this.appender.setFormatter(IdeaLogRecordFormatter())
  }

  override fun getLoggerInstance(category: String): Logger {
    val logger = java.util.logging.Logger.getLogger(category)
    val level = if (isDebugLevel(category)) Level.FINE else Level.INFO
    JulLogger.clearHandlers(logger)
    appender.setLevel(level)
    logger.addHandler(appender)
    logger.setUseParentHandlers(false)
    logger.setLevel(level)
    return JulLogger(logger)
  }

  private fun isDebugLevel(category: String): Boolean {
    return categoriesWithDebugLevel.any(category::startsWith)
  }
}

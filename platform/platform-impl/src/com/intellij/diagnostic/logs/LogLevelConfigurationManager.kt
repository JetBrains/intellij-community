// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.logs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.JulLogger
import com.intellij.openapi.diagnostic.RollingFileHandler
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.io.FileUtil.sanitizeFileName
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.LogRecord

/**
 * Allows applying & persisting custom log debug categories
 * which can be turned on by user via the [com.intellij.ide.actions.DebugLogConfigureAction].
 * Applies these custom categories at startup.
 */
@Service(Service.Level.APP)
@State(name = "Logs.Categories",
       storages = [Storage(value = "log-categories.xml", usePathMacroManager = false)],
       useLoadedStateAsExisting = false,
       reportStatistic = false)
class LogLevelConfigurationManager : SerializablePersistentStateComponent<LogLevelConfigurationManager.State>(State()) {
  companion object {
    private val LOG = logger<LogLevelConfigurationManager>()

    internal const val LOG_DEBUG_CATEGORIES_SYSTEM_PROPERTY = "idea.log.debug.categories"
    internal const val LOG_TRACE_CATEGORIES_SYSTEM_PROPERTY = "idea.log.trace.categories"
    internal const val LOG_ALL_CATEGORIES_SYSTEM_PROPERTY = "idea.log.all.categories"
    internal const val LOG_SEPARATE_FILE_CATEGORIES_SYSTEM_PROPERTY = "idea.log.separate.file.categories"

    @JvmStatic
    fun getInstance(): LogLevelConfigurationManager = service()
  }

  @Internal
  interface Listener {
    /**
     * Instead of sending changes diff,
     * it is supposed that implementations look at [com.intellij.openapi.diagnostic.Logger.isDebugEnabled] themselves.
     */
    fun onCategoriesChanged()

    companion object {
      @JvmField
      val EP_NAME: ExtensionPointName<Listener> = ExtensionPointName.create("com.intellij.logLevelConfigurationListener")
    }
  }

  private val customizedLoggers = hashMapOf<String, java.util.logging.Logger>()
  private val lock = Object()

  @Internal
  fun setCategories(state: State) {
    cleanCurrentCategories()
    addCategories(state)
  }

  fun setCategories(categories: List<LogCategory>) {
    cleanCurrentCategories()
    addCategories(categories)
  }

  fun addCategories(categories: List<LogCategory>) {
    addCategories(State(categories = categories))
  }

  @Internal
  fun addCategories(state: State) {
    val appliedCategories = applyState(state)
    updateState {
      appliedCategories
    }
    Listener.EP_NAME.forEachExtensionSafe { it.onCategoriesChanged() }
  }

  private fun String.toTrimmed(): String = trimStart('#')

  private fun getJulLoggerFromCache(category: String): java.util.logging.Logger =
    synchronized(lock) {
      customizedLoggers.computeIfAbsent(category, java.util.logging.Logger::getLogger)
    }

  private fun applyState(state: State): State {
    val updatedCategoriesToLevel = updatedCategoriesToLevel(state.categories)
    updatedCategoriesToLevel.forEach { (category, level) ->
      val loggerLevel = when (level) {
        DebugLogLevel.DEBUG -> Level.FINE
        DebugLogLevel.TRACE -> Level.FINER
        DebugLogLevel.ALL -> Level.ALL
      }

      // IDEA-297747 - convention for category naming is unclear, so set logging for both with '#' and without '#'
      addLogger("#${category}", loggerLevel, level)
      addLogger(category, loggerLevel, level)
    }

    val allObservedCategories = updatedCategoriesToLevel.keys union this.state.categories.map { it.category.toTrimmed() }
    val categoriesWithSeparateFiles = state.categoriesWithSeparateFiles.map { it.toTrimmed() } intersect allObservedCategories

    for (category in allObservedCategories) {
      val enable = category in categoriesWithSeparateFiles
      setSeparateFile(getJulLoggerFromCache("#$category"), enable)
      setSeparateFile(getJulLoggerFromCache(category), enable)
    }

    return State(
      categories = updatedCategoriesToLevel.map { LogCategory(it.key, it.value) },
      categoriesWithSeparateFiles = categoriesWithSeparateFiles,
    )
  }

  private fun addLogger(trimmed: String, loggerLevel: Level?, level: DebugLogLevel) {
    val logger = getJulLoggerFromCache(trimmed)
    logger.level = loggerLevel
    LOG.info("Level ${level.name} is set for the following category: $trimmed")
  }

  private fun setSeparateFile(logger: java.util.logging.Logger, enable: Boolean) {
    val existingHandler = logger.handlers.find { it is SeparateFileHandler }
    when {
      enable && existingHandler == null -> {
        logger.useParentHandlers = false
        logger.addHandler(SeparateFileHandler(logger.name, logger.parent))
        LOG.info("Debug logs are written in a separate file for the following category: ${logger.name}")
      }
      !enable && existingHandler != null -> {
        logger.useParentHandlers = true
        logger.removeHandler(existingHandler)
        LOG.info("Debug logs are not written anymore in a separate file for the following category: ${logger.name}")
      }
    }
  }

  private class SeparateFileHandler(
    private val category: String,
    private val parentLogger: java.util.logging.Logger?,
  ) : java.util.logging.Handler() {
    private val separateHandler = lazy {
      val logRoot =
        if (ApplicationManager.getApplication()?.isUnitTestMode == true)
          PathManager.getSystemDir().resolve("testlog")
        else
          Path.of(PathManager.getLogPath())
      val logFileName = "idea_${sanitizeFileName(IdeaLogRecordFormatter.smartAbbreviate(category.trimStart('#')), true, ".")}.log"
      val handler = RollingFileHandler(
        logPath = logRoot.resolve(logFileName),
        limit = JulLogger.LOG_FILE_SIZE_LIMIT,
        count = JulLogger.LOG_FILE_COUNT,
        append = true,
      )
      handler.level = Level.ALL
      handler.formatter = java.util.logging.Logger.getLogger("").handlers.first { it is RollingFileHandler }.formatter
      handler
    }

    override fun publish(record: LogRecord) {
      if (record.level.intValue() >= Level.INFO.intValue()) {
        parentLogger?.log(record)
      }
      separateHandler.value.publish(record)
    }

    override fun flush() {
      if (separateHandler.isInitialized()) {
        separateHandler.value.flush()
      }
    }

    override fun close() {
      if (separateHandler.isInitialized()) {
        separateHandler.value.close()
      }
    }
  }

  private fun updatedCategoriesToLevel(categoriesToUpdate: List<LogCategory>): Map<String, DebugLogLevel> {
    val trimmedCategories = getCategories().associate { (category, level) -> category.toTrimmed() to level }.toMutableMap()
    val toUpdateTrimmedCategories = categoriesToUpdate.associate { (category, level) -> category.toTrimmed() to level }
    for ((newCategory, newLevel) in toUpdateTrimmedCategories) {
      val foundLevel = trimmedCategories[newCategory]
      if (foundLevel == null || foundLevel.ordinal < newLevel.ordinal) {
        trimmedCategories[newCategory] = newLevel
      }
    }
    return trimmedCategories
  }

  private fun cleanCurrentCategories() {
    synchronized(lock) {
      for ((category, logger) in customizedLoggers) {
        setSeparateFile(logger, false)
        logger.level = null
      }
      customizedLoggers.clear()
    }
    updateState {
      it.copy(categories = emptyList())
    }
  }

  fun getCategories(): List<LogCategory> = this.state.categories

  @Serializable
  @Internal
  data class State(
    @JvmField val categories: List<LogCategory> = listOf(),
    @JvmField val categoriesWithSeparateFiles: Set<String> = setOf(),
  )

  @Internal
  override fun loadState(state: State) {
    super.loadState(state)
    val fromSystemProperties = collectStateFromSystemProperties()
    applyState(State(
      categories = state.categories + fromSystemProperties.categories,
      categoriesWithSeparateFiles = state.categoriesWithSeparateFiles + fromSystemProperties.categoriesWithSeparateFiles,
    ))
  }

  override fun noStateLoaded() {
    val categories = collectStateFromSystemProperties()
    applyState(categories)
  }

  private fun collectStateFromSystemProperties(): State {
    val categories = mutableListOf<LogCategory>()
    // add categories from system properties (e.g., for tests on CI server)
    categories.addAll(fromString(System.getProperty(LOG_DEBUG_CATEGORIES_SYSTEM_PROPERTY), DebugLogLevel.DEBUG))
    categories.addAll(fromString(System.getProperty(LOG_TRACE_CATEGORIES_SYSTEM_PROPERTY), DebugLogLevel.TRACE))
    categories.addAll(fromString(System.getProperty(LOG_ALL_CATEGORIES_SYSTEM_PROPERTY), DebugLogLevel.ALL))
    return State(
      categories = categories,
      categoriesWithSeparateFiles = separateFileFromString(System.getProperty(LOG_SEPARATE_FILE_CATEGORIES_SYSTEM_PROPERTY)),
    )
  }

  private fun fromString(text: String?, level: DebugLogLevel): List<LogCategory> {
    val categories = splitCategories(text) ?: return emptyList<LogCategory>()
    return categories.mapNotNull { if (it.isBlank()) null else LogCategory(it, level) }
  }

  private fun separateFileFromString(text: String?): Set<String> =
    splitCategories(text)?.toHashSet()
    ?: emptySet()

  private fun splitCategories(text: String?): List<String>? {
    if (text.isNullOrBlank()) {
      return null
    }

    val byNewlines = text.lines()
    val byCommas = text.split(',')
    if (byCommas.size > 1 && byNewlines.size > 1) {
      LOG.error("Mixed commas and newlines as category separators: $text")
    }
    return if (byCommas.size > byNewlines.size) byCommas else byNewlines
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.logs

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.logging.Level

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

    private const val LOG_DEBUG_CATEGORIES_SYSTEM_PROPERTY = "idea.log.debug.categories"
    private const val LOG_TRACE_CATEGORIES_SYSTEM_PROPERTY = "idea.log.trace.categories"
    private const val LOG_ALL_CATEGORIES_SYSTEM_PROPERTY = "idea.log.all.categories"

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

  private val customizedLoggers = mutableListOf<java.util.logging.Logger>()
  private val lock = Object()

  fun setCategories(categories: List<LogCategory>) {
    cleanCurrentCategories()
    addCategories(categories)
  }

  fun addCategories(categories: List<LogCategory>) {
    val appliedCategories = applyCategories(categories)
    updateState {
      it.copy(categories = appliedCategories)
    }
    Listener.EP_NAME.forEachExtensionSafe { it.onCategoriesChanged() }
  }

  private fun String.toTrimmed(): String = trimStart('#')

  private fun applyCategories(categories: List<LogCategory>): List<LogCategory> {
    val updatedCategoriesToLevel = updatedCategoriesToLevel(categories)
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
    return updatedCategoriesToLevel.map { LogCategory(it.key, it.value) }
  }

  private fun addLogger(trimmed: String, loggerLevel: Level?, level: DebugLogLevel) {
    val logger = java.util.logging.Logger.getLogger(trimmed)
    logger.level = loggerLevel
    synchronized(lock) {
      customizedLoggers.add(logger)
    }
    LOG.info("Level ${level.name} is set for the following category: $trimmed")
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
      for (category in customizedLoggers) {
        category.level = null
      }
      customizedLoggers.clear()
    }
    updateState {
      it.copy(categories = emptyList())
    }
  }

  fun getCategories(): List<LogCategory> = this.state.categories

  @Serializable
  data class State(@JvmField val categories: List<LogCategory> = listOf())

  override fun loadState(state: State) {
    super.loadState(state)
    val categories = state.categories + collectCategoriesFromSystemProperties()
    applyCategories(categories)
  }

  override fun noStateLoaded() {
    val categories = collectCategoriesFromSystemProperties()
    applyCategories(categories)
  }

  private fun collectCategoriesFromSystemProperties(): List<LogCategory> {
    val categories = mutableListOf<LogCategory>()
    // add categories from system properties (e.g., for tests on CI server)
    categories.addAll(fromString(System.getProperty(LOG_DEBUG_CATEGORIES_SYSTEM_PROPERTY), DebugLogLevel.DEBUG))
    categories.addAll(fromString(System.getProperty(LOG_TRACE_CATEGORIES_SYSTEM_PROPERTY), DebugLogLevel.TRACE))
    categories.addAll(fromString(System.getProperty(LOG_ALL_CATEGORIES_SYSTEM_PROPERTY), DebugLogLevel.ALL))
    return categories
  }

  private fun fromString(text: String?, level: DebugLogLevel): List<LogCategory> {
    if (text == null) {
      return emptyList()
    }

    val byNewlines = text.lines()
    val byCommas = text.split(',')
    if (byCommas.size > 1 && byNewlines.size > 1) {
      LOG.error("Mixed commas and newlines as category separators: $text")
    }
    val categories = if (byCommas.size > byNewlines.size) byCommas else byNewlines
    return categories.mapNotNull { if (it.isBlank()) null else LogCategory(it, level) }
  }
}

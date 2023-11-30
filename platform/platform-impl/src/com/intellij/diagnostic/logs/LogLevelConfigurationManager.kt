// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.logs

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.Serializable
import java.util.logging.Level

/**
 * Allows applying & persisting custom log debug categories
 * which can be turned on by user via the [com.intellij.ide.actions.DebugLogConfigureAction].
 * Applies these custom categories on startup.
 */
@Service(Service.Level.APP)
@State(name = "Logs.Categories",
       storages = [Storage(value = "log-categories.xml", usePathMacroManager = false)],
       useLoadedStateAsExisting = false,
       reportStatistic = false)
class LogLevelConfigurationManager : SerializablePersistentStateComponent<LogLevelConfigurationManager.State>(State()) {

  companion object {
    private val LOG = Logger.getInstance(LogLevelConfigurationManager::class.java)

    private const val LOG_DEBUG_CATEGORIES = "log.debug.categories"
    private const val LOG_TRACE_CATEGORIES = "log.trace.categories"
    private const val LOG_ALL_CATEGORIES = "log.all.categories"
    private const val LOG_DEBUG_CATEGORIES_SYSTEM_PROPERTY = "idea.$LOG_DEBUG_CATEGORIES"
    private const val LOG_TRACE_CATEGORIES_SYSTEM_PROPERTY = "idea.$LOG_TRACE_CATEGORIES"
    private const val LOG_ALL_CATEGORIES_SYSTEM_PROPERTY = "idea.$LOG_ALL_CATEGORIES"

    @JvmStatic
    fun getInstance(): LogLevelConfigurationManager = service()
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
  }

  private fun applyCategories(categories: List<LogCategory>): List<LogCategory> {
    val filteredCategories = filteredCategories(categories)
    return filteredCategories.map { logCategory ->
      val level = logCategory.level
      val loggerLevel = when (level) {
        DebugLogLevel.DEBUG -> Level.FINE
        DebugLogLevel.TRACE -> Level.FINER
        DebugLogLevel.ALL -> Level.ALL
      }

      val trimmed = logCategory.category.trim('#')
      // IDEA-297747 Convention for categories naming is not clear, so set logging for both with '#' and without '#'
      addLogger(logCategory.category, loggerLevel, level)
      addLogger("#$trimmed", loggerLevel, level)
      addLogger(trimmed, loggerLevel, level)
      LogCategory(trimmed, level)
    }
  }

  private fun addLogger(trimmed: String, loggerLevel: Level?, level: DebugLogLevel) {
    val logger = java.util.logging.Logger.getLogger(trimmed)
    logger.level = loggerLevel
    synchronized(lock) {
      customizedLoggers.add(logger)
    }
    LOG.info("Level ${level.name} is set for the following category: $trimmed")
  }

  private fun filteredCategories(categories: List<LogCategory>): List<LogCategory> {
    val currentCategories = getCategories().toMutableList()
    for (newCat in categories) {
      var level: DebugLogLevel = newCat.level
      val found = currentCategories.find { curCat ->
        if (curCat.category == newCat.category) {
          val verbose = maxOf(curCat.level.ordinal, newCat.level.ordinal)
          level = DebugLogLevel.entries.toTypedArray()[verbose]
          return@find true
        }
        else false
      }
      found?.let { currentCategories.remove(it) }
      currentCategories.add(LogCategory(newCat.category, level))
    }
    return currentCategories
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

  fun getCategories(): List<LogCategory> {
    return this.state.categories
  }

  @Serializable
  data class State(@JvmField val categories: List<LogCategory> = listOf())

  //init {
  //  loadState(State())
  //}

  override fun loadState(state: State) {
    super.loadState(state)
    applyCategories(state.categories.toList())
    applyCategoriesFromProperties()
  }

  private fun applyCategoriesFromProperties() {
    val categories = mutableListOf<LogCategory>()
    categories.addAll(getSavedCategories())

    // add categories from system properties (e.g., for tests on CI server)
    categories.addAll(fromString(System.getProperty(LOG_DEBUG_CATEGORIES_SYSTEM_PROPERTY), DebugLogLevel.DEBUG))
    categories.addAll(fromString(System.getProperty(LOG_TRACE_CATEGORIES_SYSTEM_PROPERTY), DebugLogLevel.TRACE))
    categories.addAll(fromString(System.getProperty(LOG_ALL_CATEGORIES_SYSTEM_PROPERTY), DebugLogLevel.ALL))
    applyCategories(categories)
  }

  override fun noStateLoaded() {
    applyCategoriesFromProperties()
  }

  private fun getSavedCategories(): List<LogCategory> {
    val properties = PropertiesComponent.getInstance()
    return fromString(properties.getValue(LOG_DEBUG_CATEGORIES), DebugLogLevel.DEBUG) +
           fromString(properties.getValue(LOG_TRACE_CATEGORIES), DebugLogLevel.TRACE) +
           fromString(properties.getValue(LOG_ALL_CATEGORIES), DebugLogLevel.ALL)
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

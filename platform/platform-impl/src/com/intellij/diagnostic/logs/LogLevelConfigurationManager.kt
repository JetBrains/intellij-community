// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.logs

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
    filteredCategories.map { logCategory ->
      val level = logCategory.level
      val loggerLevel = when (level) {
        DebugLogLevel.DEBUG -> Level.FINE
        DebugLogLevel.TRACE -> Level.FINER
        DebugLogLevel.ALL -> Level.ALL
      }

      val trimmed = logCategory.category.trimStart('#')

      // IDEA-297747 Convention for categories naming is not clear, so set logging for both with '#' and without '#'
      addLogger(logCategory.category, loggerLevel, level)
      addLogger("#$trimmed", loggerLevel, level)
      addLogger(trimmed, loggerLevel, level)
    }
    return filteredCategories
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
      val found = currentCategories.find { curCat ->
        if (curCat.category == newCat.category) {
          val verbose = maxOf(curCat.level.ordinal, newCat.level.ordinal)
          curCat.level = DebugLogLevel.entries.toTypedArray()[verbose]
          return@find true
        }
        else false
      }
      if (found == null) {
        currentCategories.add(newCat)
      }
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

  override fun loadState(state: State) {
    super.loadState(state)
    applyCategories(state.categories)
  }
}


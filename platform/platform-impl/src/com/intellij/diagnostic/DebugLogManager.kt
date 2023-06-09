// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import java.util.logging.Level
import java.util.logging.Logger

private const val LOG_DEBUG_CATEGORIES = "log.debug.categories"
private const val LOG_TRACE_CATEGORIES = "log.trace.categories"
private const val LOG_ALL_CATEGORIES = "log.all.categories"
private const val LOG_DEBUG_CATEGORIES_SYSTEM_PROPERTY = "idea.$LOG_DEBUG_CATEGORIES"
private const val LOG_TRACE_CATEGORIES_SYSTEM_PROPERTY = "idea.$LOG_TRACE_CATEGORIES"
private const val LOG_ALL_CATEGORIES_SYSTEM_PROPERTY = "idea.$LOG_ALL_CATEGORIES"

/**
 * Allows applying & persisting custom log debug categories
 * which can be turned on by user via the [com.intellij.ide.actions.DebugLogConfigureAction].
 * Applies these custom categories on startup.
 */
@Suppress("LightServiceMigrationCode")
class DebugLogManager {
  enum class DebugLogLevel { DEBUG, TRACE, ALL }

  data class Category(val category: String, val level: DebugLogLevel)

  companion object {
    @JvmStatic
    fun getInstance(): DebugLogManager = service()
  }

  // java.util.logging keeps only weak references to loggers, so we need to store strong references to loggers we've customized to ensure
  // that a logger can't get garbage-collected and then recreated with a default level instead of a customized one
  private val customizedLoggers = mutableListOf<Logger>()

  init {
    val categories = mutableListOf<Category>()
    categories.addAll(getSavedCategories())
    // add categories from system properties (e.g., for tests on CI server)
    categories.addAll(fromString(System.getProperty(LOG_DEBUG_CATEGORIES_SYSTEM_PROPERTY), DebugLogLevel.DEBUG))
    categories.addAll(fromString(System.getProperty(LOG_TRACE_CATEGORIES_SYSTEM_PROPERTY), DebugLogLevel.TRACE))
    categories.addAll(fromString(System.getProperty(LOG_ALL_CATEGORIES_SYSTEM_PROPERTY), DebugLogLevel.ALL))
    applyCategories(categories)
  }

  fun getSavedCategories(): List<Category> {
    val properties = PropertiesComponent.getInstance()
    return fromString(properties.getValue(LOG_DEBUG_CATEGORIES), DebugLogLevel.DEBUG) +
           fromString(properties.getValue(LOG_TRACE_CATEGORIES), DebugLogLevel.TRACE) +
           fromString(properties.getValue(LOG_ALL_CATEGORIES), DebugLogLevel.ALL)
  }

  fun clearCategories(categories: List<Category>) {
    categories
      .flatMap {
        val trimmed = it.category.trimStart('#')
        listOf(it.category, trimmed, "#$trimmed")
      }
      .distinct()
      .forEach {
        Logger.getLogger(it)?.level = null
      }
    customizedLoggers.clear()
  }

  fun applyCategories(categories: List<Category>) {
    applyCategories(categories, DebugLogLevel.DEBUG, Level.FINE)
    applyCategories(categories, DebugLogLevel.TRACE, Level.FINER)
    applyCategories(categories, DebugLogLevel.ALL, Level.ALL)
  }

  private fun applyCategories(categories: List<Category>, level: DebugLogLevel, loggerLevel: Level) {
    val filtered = categories.asSequence()
      .filter { it.level == level }
      // IDEA-297747 Convention for categories naming is not clear, so set logging for both with '#' and without '#'
      .flatMap {
        val trimmed = it.category.trimStart('#')
        listOf(it.category, trimmed, "#$trimmed")
      }
      .distinct()
      .toList()
    for (name in filtered) {
      val logger = Logger.getLogger(name)
      logger.level = loggerLevel
      customizedLoggers.add(logger)
    }
    if (filtered.isNotEmpty()) {
      logger<DebugLogManager>().info("Set ${level.name} for the following categories: ${filtered.joinToString()}")
    }
  }

  fun saveCategories(categories: List<Category>) {
    val properties = PropertiesComponent.getInstance()
    properties.setValue(LOG_DEBUG_CATEGORIES, toString(categories, DebugLogLevel.DEBUG), null)
    properties.setValue(LOG_TRACE_CATEGORIES, toString(categories, DebugLogLevel.TRACE), null)
    properties.setValue(LOG_ALL_CATEGORIES, toString(categories, DebugLogLevel.ALL), null)
  }
}

private fun fromString(text: String?, level: DebugLogManager.DebugLogLevel): List<DebugLogManager.Category> {
  if (text == null) {
    return emptyList()
  }

  val byNewlines = text.lines()
  val byCommas = text.split(',')
  if (byCommas.size > 1 && byNewlines.size > 1) {
    error("Do not mix commas and newlines as category separators: $text")
  }
  val categories = if (byCommas.size > byNewlines.size) byCommas else byNewlines
  return categories.mapNotNull { if (it.isBlank()) null else DebugLogManager.Category(it, level) }
}


private fun toString(categories: List<DebugLogManager.Category>, level: DebugLogManager.DebugLogLevel): String? {
  val filtered = categories.asSequence().filter { it.level == level }.map { it.category }.toList()
  return if (filtered.isEmpty()) null else filtered.joinToString("\n")
}

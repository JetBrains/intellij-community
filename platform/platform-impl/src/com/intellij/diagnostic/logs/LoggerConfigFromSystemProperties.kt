// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.logs

import com.intellij.openapi.diagnostic.LogLevel
import org.jetbrains.annotations.ApiStatus
import java.util.logging.Level

@ApiStatus.Internal
object LoggerConfigFromSystemProperties {
  internal const val LOG_DEBUG_CATEGORIES_SYSTEM_PROPERTY = "idea.log.debug.categories"
  internal const val LOG_TRACE_CATEGORIES_SYSTEM_PROPERTY = "idea.log.trace.categories"
  internal const val LOG_ALL_CATEGORIES_SYSTEM_PROPERTY = "idea.log.all.categories"
  internal const val LOG_SEPARATE_FILE_CATEGORIES_SYSTEM_PROPERTY = "idea.log.separate.file.categories"

  val categoryToLevel: Map<String, LogLevel> = parseLogLevelFromSystemProperties()
  @JvmField
  val hasLogLevelSystemPropertiesConfigured: Boolean = categoryToLevel.isNotEmpty()
  private val levelLookupMap: Map<String, Level> = prepareLevelLookupMap()

  val separateLogFileCategories: List<String> = parseSeparateLogFileCategoriesFromSystemProperty()

  @JvmStatic
  fun getLevelFromSystemProperties(category: String): Level? = levelLookupMap[category]

  private fun parseLogLevelFromSystemProperties(): Map<String, LogLevel> {
    val debugCategories = System.getProperty(LOG_DEBUG_CATEGORIES_SYSTEM_PROPERTY)
    val traceCategories = System.getProperty(LOG_TRACE_CATEGORIES_SYSTEM_PROPERTY)
    val allCategories = System.getProperty(LOG_ALL_CATEGORIES_SYSTEM_PROPERTY)
    if (debugCategories == null && traceCategories == null && allCategories == null) {
      return emptyMap()
    }
    val categoryToLevel = HashMap<String, LogLevel>()
    setLevelsForCategories(debugCategories, LogLevel.DEBUG, categoryToLevel)
    setLevelsForCategories(traceCategories, LogLevel.TRACE, categoryToLevel)
    setLevelsForCategories(allCategories, LogLevel.ALL, categoryToLevel)
    if (categoryToLevel.isEmpty()) {
      return emptyMap()
    }
    return categoryToLevel
  }

  private fun splitCategories(categories: String?): List<String> {
    if (categories == null) {
      return emptyList()
    }
    val result = ArrayList<String>()
    var lastSeparator = -1
    for (i in 0..categories.length) {
      if (i == categories.length || categories[i] == ',') {
        if (lastSeparator + 1 < i) {
          val category = categories.substring(lastSeparator + 1, i)
          result.add(category)
        }
        lastSeparator = i
      }
    }
    if (result.isEmpty()) {
      return emptyList()
    }
    return result
  }

  private fun setLevelsForCategories(categories: String?, level: LogLevel, categoryToLevel: MutableMap<String, LogLevel>) {
    for (category in splitCategories(categories)) { categoryToLevel[category] = level }
  }

  private fun prepareLevelLookupMap(): Map<String, Level> {
    if (categoryToLevel.isEmpty()) {
      return emptyMap()
    }
    val lookupMap = HashMap<String, Level>(categoryToLevel.size * 2)
    for ((category, logLevel) in categoryToLevel) {
      val level = logLevel.level
      lookupMap[category] = level
      if (category.firstOrNull() == '#') {
        lookupMap[category.substring(1)] = level
      }
      else {
        lookupMap["#$category"] = level
      }
    }
    return lookupMap
  }

  private fun parseSeparateLogFileCategoriesFromSystemProperty(): List<String> {
    val separateFileCategories = System.getProperty(LOG_SEPARATE_FILE_CATEGORIES_SYSTEM_PROPERTY)
    if (separateFileCategories.isNullOrEmpty()) {
      return emptyList()
    }
    return splitCategories(separateFileCategories)
  }
}

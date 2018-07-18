// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import org.apache.log4j.Level
import org.apache.log4j.LogManager

/**
 * Allows to apply & persist custom log debug categories which can be turned on by user via the [com.intellij.ide.actions.DebugLogConfigureAction].
 * Applies these custom categories on startup.
 */
class DebugLogManager(private val properties: PropertiesComponent) : ApplicationComponent {
  enum class DebugLogLevel { DEBUG, TRACE }

  override fun initComponent() {
    val categories =
      getSavedCategories() +
      // add categories from system properties (e.g. for tests on CI server)
      fromString(System.getProperty(LOG_DEBUG_CATEGORIES_SYSTEM_PROPERTY), DebugLogLevel.DEBUG) +
      fromString(System.getProperty(LOG_TRACE_CATEGORIES_SYSTEM_PROPERTY), DebugLogLevel.TRACE)

    applyCategories(categories)
  }

  fun getSavedCategories(): List<Pair<String, DebugLogLevel>> =
    fromString(properties.getValue(LOG_DEBUG_CATEGORIES), DebugLogLevel.DEBUG) +
    fromString(properties.getValue(LOG_TRACE_CATEGORIES), DebugLogLevel.TRACE)

  fun clearCategories(categories: List<Pair<String, DebugLogLevel>>) {
    categories.forEach {
      LogManager.getLogger(it.first)?.level = null
    }
  }

  fun applyCategories(categories: List<Pair<String, DebugLogLevel>>) {
    applyCategories(categories, DebugLogLevel.DEBUG, Level.DEBUG)
    applyCategories(categories, DebugLogLevel.TRACE, Level.TRACE)
  }

  private fun applyCategories(categories: List<Pair<String, DebugLogLevel>>, level: DebugLogLevel, log4jLevel: Level) {
    val filtered = categories.filter { it.second == level }.map { it.first }
    filtered.forEach {
      LogManager.getLogger(it)?.level = log4jLevel
    }
    if (!filtered.isEmpty()) {
      LOG.info("Set ${level.name} for the following categories: ${filtered.joinToString()}")
    }
  }

  fun saveCategories(categories: List<Pair<String, DebugLogLevel>>) {
    properties.setValue(LOG_DEBUG_CATEGORIES, toString(categories, DebugLogLevel.DEBUG), null)
    properties.setValue(LOG_TRACE_CATEGORIES, toString(categories, DebugLogLevel.TRACE), null)
  }

  private fun fromString(text: String?, level: DebugLogLevel) =
    if (text != null) StringUtil.splitByLines(text, true).map { it to level }.toList() else emptyList()

  private fun toString(categories: List<Pair<String, DebugLogLevel>>, level: DebugLogLevel): String? {
    val filtered = categories.filter { it.second == level }.map { it.first }
    return if (filtered.isNotEmpty()) filtered.joinToString("\n") else null
  }
}

private val LOG_DEBUG_CATEGORIES = "log.debug.categories"
private val LOG_TRACE_CATEGORIES = "log.trace.categories"
private val LOG_DEBUG_CATEGORIES_SYSTEM_PROPERTY = "idea." + LOG_DEBUG_CATEGORIES
private val LOG_TRACE_CATEGORIES_SYSTEM_PROPERTY = "idea." + LOG_TRACE_CATEGORIES

private val LOG = Logger.getInstance(DebugLogManager::class.java)
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.ContainerUtil
import org.apache.log4j.Level
import org.apache.log4j.LogManager

/**
 * Allows to apply & persist custom log debug categories which can be turned on by user via the [com.intellij.ide.actions.DebugLogConfigureAction].
 * Applies these custom categories on startup.
 */
class DebugLogManager {
  enum class DebugLogLevel { DEBUG, TRACE }

  data class Category(val category: String, val level: DebugLogLevel)

  companion object {
    @JvmStatic
    fun getInstance() = service<DebugLogManager>()
  }

  init {
    val categories = mutableListOf<Category>()
    categories.addAll(getSavedCategories())
    // add categories from system properties (e.g. for tests on CI server)
    categories.addAll(fromString(System.getProperty(LOG_DEBUG_CATEGORIES_SYSTEM_PROPERTY), DebugLogLevel.DEBUG))
    categories.addAll(fromString(System.getProperty(LOG_TRACE_CATEGORIES_SYSTEM_PROPERTY), DebugLogLevel.TRACE))
    applyCategories(categories)
  }

  fun getSavedCategories(): List<Category> {
    val properties = PropertiesComponent.getInstance()
    return ContainerUtil.concat(fromString(properties.getValue(LOG_DEBUG_CATEGORIES), DebugLogLevel.DEBUG),
                                fromString(properties.getValue(LOG_TRACE_CATEGORIES), DebugLogLevel.TRACE))
  }

  fun clearCategories(categories: List<Category>) {
    categories.forEach {
      LogManager.getLogger(it.category)?.level = null
    }
  }

  fun applyCategories(categories: List<Category>) {
    applyCategories(categories, DebugLogLevel.DEBUG, Level.DEBUG)
    applyCategories(categories, DebugLogLevel.TRACE, Level.TRACE)
  }

  private fun applyCategories(categories: List<Category>, level: DebugLogLevel, log4jLevel: Level) {
    val filtered = categories.asSequence().filter { it.level == level }.map { it.category }.toList()
    filtered.forEach {
      LogManager.getLogger(it)?.level = log4jLevel
    }
    if (filtered.isNotEmpty()) {
      LOG.info("Set ${level.name} for the following categories: ${filtered.joinToString()}")
    }
  }

  fun saveCategories(categories: List<Category>) {
    val properties = PropertiesComponent.getInstance()
    properties.setValue(LOG_DEBUG_CATEGORIES, toString(categories, DebugLogLevel.DEBUG), null)
    properties.setValue(LOG_TRACE_CATEGORIES, toString(categories, DebugLogLevel.TRACE), null)
  }

  private fun fromString(text: String?, level: DebugLogLevel): List<Category> {
    return when {
      text != null -> {
        val byNewlines = text.lineSequence().toList()
        val byCommas = text.split(",")
        if (byCommas.size > 1 && byNewlines.size > 1) error("Do not mix commas and newlines as category separators: $text")
        val categories = if (byCommas.size > byNewlines.size) byCommas else byNewlines
        categories.mapNotNull { if (it.isBlank()) null else Category(it, level) }.toList()
      }
      else -> emptyList()
    }
  }

  private fun toString(categories: List<Category>, level: DebugLogLevel): String? {
    val filtered = categories.asSequence().filter { it.level == level }.map { it.category }.toList()
    return if (filtered.isNotEmpty()) filtered.joinToString("\n") else null
  }
}

private const val LOG_DEBUG_CATEGORIES = "log.debug.categories"
private const val LOG_TRACE_CATEGORIES = "log.trace.categories"
private const val LOG_DEBUG_CATEGORIES_SYSTEM_PROPERTY = "idea.$LOG_DEBUG_CATEGORIES"
private const val LOG_TRACE_CATEGORIES_SYSTEM_PROPERTY = "idea.$LOG_TRACE_CATEGORIES"

private val LOG = logger<DebugLogManager>()
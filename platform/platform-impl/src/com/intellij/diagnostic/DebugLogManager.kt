/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
class DebugLogManager : ApplicationComponent.Adapter() {
  enum class DebugLogLevel { DEBUG, TRACE }

  fun getSavedCategories(): List<Pair<String, DebugLogLevel>> {
    val properties = PropertiesComponent.getInstance()
    return fromString(properties.getValue(LOG_DEBUG_CATEGORIES), DebugLogLevel.DEBUG) +
           fromString(properties.getValue(LOG_TRACE_CATEGORIES), DebugLogLevel.TRACE)
  }

  override fun initComponent() {
    val categories = getSavedCategories()
    if (categories.isEmpty()) {
      saveCategories(getCurrentCategories())
    }
    else {
      applyCategories(categories)
    }
  }

  private fun fromString(text: String?, level: DebugLogLevel) =
    if (text != null) StringUtil.splitByLines(text, true).map { Pair(it, level) }.toList() else emptyList()

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
    PropertiesComponent.getInstance().setValue(LOG_DEBUG_CATEGORIES, toString(categories, DebugLogLevel.DEBUG), null)
    PropertiesComponent.getInstance().setValue(LOG_TRACE_CATEGORIES, toString(categories, DebugLogLevel.TRACE), null)
  }

  private fun toString(categories: List<Pair<String, DebugLogLevel>>, level: DebugLogLevel): String? {
    val filtered = categories.filter { it.second == level }.map { it.first }
    return if (filtered.isNotEmpty()) filtered.joinToString("\n") else null
  }

  private fun getCurrentCategories(): List<Pair<String, DebugLogLevel>> {
    val currentLoggers = LogManager.getCurrentLoggers().toList().filterIsInstance(org.apache.log4j.Logger::class.java)
    return currentLoggers.map {
      val category = it.name
      val logger = Logger.getInstance(category)
      when {
        logger.isTraceEnabled -> Pair(category, DebugLogLevel.TRACE)
        logger.isDebugEnabled ->  Pair(category, DebugLogLevel.DEBUG)
        else -> null
      }
    }.filterNotNull()

  }
}

private val LOG_DEBUG_CATEGORIES = "log.debug.categories"
private val LOG_TRACE_CATEGORIES = "log.trace.categories"
private val LOG = Logger.getInstance(DebugLogManager::class.java)

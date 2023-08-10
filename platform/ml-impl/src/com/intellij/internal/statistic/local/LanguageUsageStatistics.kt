// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.local

import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XMap


@State(name = "LanguageUsageStatistics", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)], reportStatistic = false)
internal class LanguageUsageStatisticsProviderImpl : PersistentStateComponent<LanguageUsageStatisticsProviderImpl.LanguageStatisticsState>,
                                                     LanguageUsageStatisticsProvider,
                                                     SimpleModificationTracker() {
  @Volatile
  private var state = LanguageStatisticsState()

  override fun getState() = state

  override fun loadState(state: LanguageStatisticsState) {
    this.state = state
  }

  override fun getStatisticsForLanguage(language: Language): LanguageUsageStatistics {
    return state.data[language.id]
             ?.let { stats ->
               val isMostUsed = language.id == getMostUsedLanguage()
               val isMostRecent = language.id == getMostRecentlyUsedLanguage()

               LanguageUsageStatistics(stats.useCount, isMostUsed, stats.lastUsed, isMostRecent)
             } ?: LanguageUsageStatistics.NEVER_USED
  }

  override fun getStatistics(): Map<String, LanguageUsageStatistics> {
    val mostUsedLanguage = getMostUsedLanguage() ?: return emptyMap()
    val mostRecentLanguage = getMostRecentlyUsedLanguage() ?: return emptyMap()

    return state.data
      .mapValues { (language, stats) ->
        val isMostUsed = mostUsedLanguage == language
        val isMostRecent = mostRecentLanguage == language

        LanguageUsageStatistics(stats.useCount, isMostUsed, stats.lastUsed, isMostRecent)
      }
  }

  private fun getMostUsedLanguage(): String? = state.data.maxByOrNull { it.value.useCount }?.key

  private fun getMostRecentlyUsedLanguage(): String? = state.data.maxByOrNull { it.value.lastUsed }?.key

  override fun updateLanguageStatistics(language: Language) {
    val stats = state.data.computeIfAbsent(language.id) { SimpleLanguageUsageStatistics() }
    stats.apply {
      useCount++
      lastUsed = System.currentTimeMillis()
    }

    incModificationCount()
  }

  data class LanguageStatisticsState(
    @get:XMap(entryTagName = "language", keyAttributeName = "id")
    @get:Property(surroundWithTag = false)
    val data: MutableMap<String, SimpleLanguageUsageStatistics> = HashMap()
  )

  @Tag("summary")
  data class SimpleLanguageUsageStatistics(@Attribute("usageCount") @JvmField var useCount: Int,
                                           @Attribute("lastUsage") @JvmField var lastUsed: Long) {
    constructor() : this(0, 0)
  }
}

private class LanguageUsageUpdaterListener : FileEditorManagerListener {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    val service = source.project.getService(LanguageUsageStatisticsProvider::class.java)
    val language = LanguageUtil.getFileLanguage(file) ?: return
    service.updateLanguageStatistics(language)
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.ide.bookmarks.BookmarkManager
import com.intellij.ide.favoritesTreeView.AbstractUrlFavoriteAdapter
import com.intellij.ide.favoritesTreeView.FavoriteNodeProvider
import com.intellij.ide.favoritesTreeView.FavoritesManager
import com.intellij.ide.projectView.impl.DirectoryUrl
import com.intellij.ide.projectView.impl.PsiFileUrl
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project

class BookmarkUsagesCollector : ProjectUsagesCollector() {
  private val group: EventLogGroup = EventLogGroup("bookmarks", 1)

  private val bookmarksTotal = group.registerEvent("bookmarks.total", EventFields.Count)
  private val bookmarksWithLine = group.registerEvent("bookmarks.with.line", EventFields.Count)
  private val bookmarksWithNumber = group.registerEvent("bookmarks.with.number.mnemonic", EventFields.Count)
  private val bookmarksWithLetter = group.registerEvent("bookmarks.with.letter.mnemonic", EventFields.Count)

  private val favoritesLists = group.registerEvent("favorites.lists", EventFields.Count)
  private val favoritesTotal = group.registerEvent("favorites.total", EventFields.Count)
  private val favoriteFiles = group.registerEvent("favorites.files", EventFields.Count)
  private val favoriteDirectories = group.registerEvent("favorites.directories", EventFields.Count)
  private val favoriteCustom = group.registerEvent("favorites.custom", EventFields.Count,
                                                   EventFields.StringValidatedByCustomRule("type", "favorite_type"),
                                                   EventFields.PluginInfoFromInstance)

  override fun getGroup(): EventLogGroup {
    return group
  }

  override fun getMetrics(project: Project): MutableSet<MetricEvent> {
    val result = mutableSetOf<MetricEvent>()

    val bookmarkManager = BookmarkManager.getInstance(project)
    val bookmarks = bookmarkManager.allBookmarks

    result += bookmarksTotal.metric(bookmarks.size)
    result += bookmarksWithLine.metric(bookmarks.filter { it.hasLine() }.size)
    result += bookmarksWithNumber.metric(bookmarks.filter { it.mnemonic in '0'..'9' }.size)
    result += bookmarksWithLetter.metric(bookmarks.filter { it.mnemonic in 'A'..'Z' }.size)

    val favoritesManager = FavoritesManager.getInstance(project)
    val listNames = favoritesManager.availableFavoritesListNames
    result += favoritesLists.metric(listNames.size)

    var favTotal = 0
    var favFiles = 0
    var favDirectories = 0
    val favCustom = mutableMapOf<String, Int>()
    val favCustomProviders = mutableMapOf<String, FavoriteNodeProvider>()
    for (listName in listNames) {
      val urls = favoritesManager.getFavoritesListRootUrls(listName)
      favTotal += urls.size
      for (url in urls) {
        when (val abstractUrl = url.data.first) {
          is PsiFileUrl -> favFiles++
          is DirectoryUrl -> favDirectories++
          is AbstractUrlFavoriteAdapter -> {
            val type = abstractUrl.nodeProvider.favoriteTypeId
            val count = favCustom.getOrPut(type) { 0 }
            favCustom[type] = count + 1
            favCustomProviders[type] = abstractUrl.nodeProvider
          }
        }
      }
    }
    result += favoritesTotal.metric(favTotal)
    result += favoriteFiles.metric(favFiles)
    result += favoriteDirectories.metric(favDirectories)
    for ((type, count) in favCustom) {
      result += favoriteCustom.metric(count, type, favCustomProviders[type]!!)
    }

    return result
  }
}

class BookmarkCounterCollector : CounterUsagesCollector() {
  enum class MnemonicType { Number, Letter, None }

  companion object {
    private val group: EventLogGroup = EventLogGroup("bookmarks.counters", 2)

    @JvmField
    val favoritesNavigate = group.registerEvent("favorites.navigate", EventFields.Class("navigatable"))
    @JvmField
    val bookmarkNavigate = group.registerEvent("bookmark.navigate",
                                               EventFields.Enum("mnemonicType", MnemonicType::class.java),
                                               EventFields.Boolean("withLine"))
  }

  override fun getGroup(): EventLogGroup = Companion.group
}

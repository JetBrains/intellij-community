// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class BookmarkUsagesFUSCollector : ProjectUsagesCollector() {
  private val group: EventLogGroup = EventLogGroup("bookmarks", 3)

  private val bookmarksTotal = group.registerEvent("bookmarks.total", EventFields.Count)
  private val bookmarksGroups = group.registerEvent("bookmarks.lists", EventFields.Count)
  private val bookmarksWithLine = group.registerEvent("bookmarks.with.line", EventFields.Count)
  private val bookmarksWithNumber = group.registerEvent("bookmarks.with.number.mnemonic", EventFields.Count)
  private val bookmarksWithLetter = group.registerEvent("bookmarks.with.letter.mnemonic", EventFields.Count)

  override fun getGroup(): EventLogGroup {
    return group
  }

  override fun getMetrics(project: Project): MutableSet<MetricEvent> {
    val result = mutableSetOf<MetricEvent>()

    val bookmarksManager = BookmarksManager.getInstance(project) ?: return result
    val bookmarks = bookmarksManager.bookmarks

    result += bookmarksTotal.metric(bookmarks.size)
    result += bookmarksGroups.metric(bookmarksManager.groups.size)
    result += bookmarksWithLine.metric(bookmarks.filterIsInstance<LineBookmark>().size)
    result += bookmarksWithNumber.metric(bookmarks.filter { bookmarksManager.getType(it)?.mnemonic in '0'..'9' }.size)
    result += bookmarksWithLetter.metric(bookmarks.filter { bookmarksManager.getType(it)?.mnemonic in 'A'..'Z' }.size)

    return result
  }
}

@ApiStatus.Internal
object BookmarkCounterCollector : CounterUsagesCollector() {
  enum class MnemonicType { Number, Letter, None }

  private val group: EventLogGroup = EventLogGroup("bookmarks.counters", 2)

  @JvmField
  val favoritesNavigate: EventId1<Class<*>?> = group.registerEvent("favorites.navigate", EventFields.Class("navigatable"))
  @JvmField
  val bookmarkNavigate: EventId2<MnemonicType, Boolean> = group.registerEvent("bookmark.navigate",
                                                                              EventFields.Enum("mnemonicType", MnemonicType::class.java),
                                                                              EventFields.Boolean("withLine"))

  override fun getGroup(): EventLogGroup = group
}

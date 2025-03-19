// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector
import com.intellij.ide.actions.RecentLocationsAction.getEmptyFileText
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.Nls
import java.util.function.Function
import kotlin.math.max
import kotlin.math.min

internal class RecentLocationsDataModel(
  private val project: Project,
  private val placesSupplier: Function<Boolean, List<IdeDocumentHistoryImpl.PlaceInfo>>?,
  private val placesRemover: ((List<IdeDocumentHistoryImpl.PlaceInfo>) -> Unit)?,
) {
  private val navigationPlaces: SynchronizedClearableLazy<List<RecentLocationItem>> = calculateItems(project, false)

  private val changedPlaces: SynchronizedClearableLazy<List<RecentLocationItem>> = calculateItems(project, true)

  private val navigationPlacesBreadcrumbsMap: Map<IdeDocumentHistoryImpl.PlaceInfo, @Nls String> by lazy {
    collectBreadcrumbs(project, navigationPlaces.value)
  }

  private val changedPlacedBreadcrumbsMap: Map<IdeDocumentHistoryImpl.PlaceInfo, @Nls String> by lazy {
    collectBreadcrumbs(project, changedPlaces.value)
  }

  fun getPlaces(changed: Boolean): List<RecentLocationItem> {
    return if (changed) changedPlaces.value else navigationPlaces.value
  }

  fun getBreadcrumbsMap(changed: Boolean): Map<IdeDocumentHistoryImpl.PlaceInfo, @Nls String> {
    return if (changed) changedPlacedBreadcrumbsMap else navigationPlacesBreadcrumbsMap
  }

  private fun collectBreadcrumbs(project: Project, items: List<RecentLocationItem>): Map<IdeDocumentHistoryImpl.PlaceInfo, @Nls String> {
    return items
      .asSequence()
      .map(RecentLocationItem::info)
      .associateBy(keySelector = { it }, valueTransform = { getBreadcrumbs(project, it) })
  }

  @Nls
  private fun getBreadcrumbs(project: Project, placeInfo: IdeDocumentHistoryImpl.PlaceInfo): String {
    val rangeMarker = placeInfo.caretPosition
    val fileName = placeInfo.file.name
    if (rangeMarker == null) {
      return fileName
    }

    val collector = FileBreadcrumbsCollector.findBreadcrumbsCollector(project, placeInfo.file) ?: return fileName
    val crumbs = collector.computeCrumbs(placeInfo.file, rangeMarker.document, rangeMarker.startOffset, true)

    if (!crumbs.iterator().hasNext()) {
      return fileName
    }

    @NlsSafe
    val separator = " > "
    @NlsSafe
    val result = crumbs.joinToString(separator) { it.text }
    return result
  }

  private fun calculateItems(project: Project, changed: Boolean): SynchronizedClearableLazy<List<RecentLocationItem>> {
    return SynchronizedClearableLazy {
      createPlaceLinePairs(project, changed)
    }
  }

  private fun createPlaceLinePairs(project: Project, changed: Boolean): List<RecentLocationItem> {
    val result = arrayListOf<RecentLocationItem>()
    if (placesSupplier != null) {
      for (place in placesSupplier.apply(changed)) {
        result.add(newLocationItem(place) ?: continue)
      }
    }
    else {
      val maxPlaces = UISettings.getInstance().recentLocationsLimit
      val places = ContainerUtil.reverse(if (changed) IdeDocumentHistory.getInstance(project).changePlaces
                                         else IdeDocumentHistory.getInstance(project).backPlaces)
      for (place in places) {
        if (result.none { IdeDocumentHistory.getInstance(project).isSame(place, it.info) }) {
          result.add(newLocationItem(place) ?: continue)
        }
        if (result.size >= maxPlaces) {
          break
        }
      }
    }
    return result
  }

  private fun newLocationItem(place: IdeDocumentHistoryImpl.PlaceInfo): RecentLocationItem? {
    val positionOffset = place.caretPosition
    if (positionOffset == null || !positionOffset.isValid) {
      return null
    }

    assert(positionOffset.startOffset == positionOffset.endOffset)

    val fileDocument = positionOffset.document
    val lineNumber = fileDocument.getLineNumber(positionOffset.startOffset)
    val ranges = getTrimmedRange(fileDocument, lineNumber)
    val documentText = ranges
      .joinToString("\n") { fileDocument.getText(it) }
      .ifEmpty { getEmptyFileText() }
    val linesShift = if (ranges.isNotEmpty()) fileDocument.getLineNumber(ranges[0].startOffset) else 0
    return RecentLocationItem(place, documentText, linesShift, ranges)
  }

  fun removeItems(project: Project, isChanged: Boolean, items: List<RecentLocationItem>) {
    if (isChanged) {
      changedPlaces.drop()
    }
    else {
      navigationPlaces.drop()
    }

    placesRemover?.let {
      it(items.map { it.info })
      return
    }

    val ideDocumentHistory = IdeDocumentHistory.getInstance(project)
    for (item in items) {
      for (it in if (isChanged) {
        ideDocumentHistory.changePlaces
      }
      else {
        ideDocumentHistory.backPlaces.filter { IdeDocumentHistory.getInstance(project).isSame(it, item.info) }
      }) {
        if (isChanged) {
          ideDocumentHistory.removeChangePlace(it)
        }
        else {
          ideDocumentHistory.removeBackPlace(it)
        }
      }
    }
  }

  private fun getTrimmedRange(document: Document, lineNumber: Int): Array<TextRange> {
    val range = getLinesRange(document, lineNumber)
    val text = document.getText(TextRange.create(range.startOffset, range.endOffset))

    val newLinesBefore = StringUtil.countNewLines(
      StringUtil.substringBefore(text, StringUtil.trimLeading(text))!!)
    val newLinesAfter = StringUtil.countNewLines(
      StringUtil.substringAfter(text, StringUtil.trimTrailing(text))!!)

    val firstLine = document.getLineNumber(range.startOffset)
    val firstLineAdjusted = firstLine + newLinesBefore

    val lastLine = document.getLineNumber(range.endOffset)
    val lastLineAdjusted = lastLine - newLinesAfter

    val result = Array(lastLineAdjusted - firstLineAdjusted + 1) {
      val startOffset = document.getLineStartOffset(firstLineAdjusted + it)
      val endOffset = document.getLineEndOffset(firstLineAdjusted + it)
      TextRange.create(startOffset, startOffset + min(endOffset - startOffset, 1000))
    }
    return result
  }

  private fun getLinesRange(document: Document, line: Int): TextRange {
    val lineCount = document.lineCount
    if (lineCount == 0) {
      return TextRange.EMPTY_RANGE
    }

    val beforeAfterLinesCount = Registry.intValue("recent.locations.lines.before.and.after", 2)

    val before = min(beforeAfterLinesCount, line)
    val after = min(beforeAfterLinesCount, lineCount - line)

    val linesBefore = before + beforeAfterLinesCount - after
    val linesAfter = after + beforeAfterLinesCount - before

    val startLine = max(line - linesBefore, 0)
    val endLine = min(line + linesAfter, lineCount - 1)

    val startOffset = document.getLineStartOffset(startLine)
    val endOffset = document.getLineEndOffset(endLine)

    return if (startOffset <= endOffset) {
      TextRange.create(startOffset, endOffset)
    }
    else {
      DocumentUtil.getLineTextRange(document, line)
    }
  }
}

internal data class RecentLocationItem(
  @JvmField val info: IdeDocumentHistoryImpl.PlaceInfo,
  @JvmField val text: String,
  @JvmField val linesShift: Int,
  @JvmField val ranges: Array<TextRange>
) {
  override fun equals(other: Any?): Boolean {
    if (other !is RecentLocationItem) {
      return false
    }

    return info.file == other.info.file &&
           linesShift == other.linesShift &&
           text.length == other.text.length &&
           ranges.size == other.ranges.size
  }

  override fun hashCode(): Int {
    var result = info.file.hashCode()
    result = 31 * result + linesShift
    result = 31 * result + text.length
    result = 31 * result + ranges.size
    return result
  }
}
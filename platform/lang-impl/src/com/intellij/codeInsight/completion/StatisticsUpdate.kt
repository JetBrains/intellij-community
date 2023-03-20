// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.featureStatistics.FeatureUsageTrackerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.WeakReferenceDisposableWrapper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.statistics.StatisticsInfo
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.util.Alarm
import org.jetbrains.annotations.VisibleForTesting

class StatisticsUpdate
    private constructor(private val myInfo: StatisticsInfo) : Disposable {
  private var mySpared: Int = 0

  override fun dispose() {}

  fun addSparedChars(lookup: Lookup, item: LookupElement, context: InsertionContext) {
    val textInserted: String
    if (context.offsetMap.containsOffset(CompletionInitializationContext.START_OFFSET) &&
        context.offsetMap.containsOffset(InsertionContext.TAIL_OFFSET) &&
        context.tailOffset >= context.startOffset) {
      textInserted = context.document.immutableCharSequence.subSequence(context.startOffset, context.tailOffset).toString()
    }
    else {
      textInserted = item.lookupString
    }
    val withoutSpaces = StringUtil.replace(textInserted, listOf(" ", "\t", "\n"), listOf("", "", ""))
    var spared = withoutSpaces.length - lookup.itemPattern(item).length
    val completionChar = context.completionChar
    if (!LookupEvent.isSpecialCompletionChar(completionChar) && withoutSpaces.contains(completionChar.toString())) {
      spared--
    }
    if (spared > 0) {
      mySpared += spared
    }
  }

  fun trackStatistics(context: InsertionContext) {
    if (ourPendingUpdate !== this) {
      return
    }

    if (!context.offsetMap.containsOffset(CompletionInitializationContext.START_OFFSET)) {
      return
    }

    val document = context.document
    val startOffset = context.startOffset
    val tailOffset =
      if (context.editor.selectionModel.hasSelection()) context.editor.selectionModel.selectionStart
      else context.editor.caretModel.offset
    if (startOffset < 0 || tailOffset <= startOffset) {
      return
    }

    val marker = document.createRangeMarker(startOffset, tailOffset)
    val listener = DocumentChangeListener(document, marker)
    document.addDocumentListener(listener)
    // Avoid hard-ref from Disposer to the document through the listener.
    // The document could be some text field with the project scope life-time,
    // don't make it leak past closing of the project.
    Disposer.register(this, WeakReferenceDisposableWrapper(listener))

    ourStatsAlarm.addRequest({
                               if (ourPendingUpdate === this) {
                                 applyLastCompletionStatisticsUpdate()
                               }
                             }, 20 * 1000)

    Disposer.register(this, Disposable {
      ourStatsAlarm.cancelAllRequests()
    })
  }

  private class DocumentChangeListener(val document: Document,
                                       val marker: RangeMarker) : DocumentListener, Disposable {
    override fun beforeDocumentChange(e: DocumentEvent) {
      if (!marker.isValid || e.offset > marker.startOffset && e.offset < marker.endOffset) {
        cancelLastCompletionStatisticsUpdate()
      }
    }

    override fun dispose() {
      document.removeDocumentListener(this)
      marker.dispose()
    }
  }

  companion object {
    private val ourStatsAlarm = Alarm(ApplicationManager.getApplication())
    private var ourPendingUpdate: StatisticsUpdate? = null

    init {
      Disposer.register(ApplicationManager.getApplication(), Disposable { cancelLastCompletionStatisticsUpdate() })
    }

    @VisibleForTesting
    @JvmStatic
    fun collectStatisticChanges(item: LookupElement): StatisticsUpdate {
      applyLastCompletionStatisticsUpdate()

      val base = StatisticsWeigher.getBaseStatisticsInfo(item, null)
      if (base === StatisticsInfo.EMPTY) {
        return StatisticsUpdate(StatisticsInfo.EMPTY)
      }

      val update = StatisticsUpdate(base)
      ourPendingUpdate = update
      Disposer.register(update, Disposable { ourPendingUpdate = null })

      return update
    }

    @JvmStatic
    fun cancelLastCompletionStatisticsUpdate() {
      ourPendingUpdate?.let { Disposer.dispose(it) }
      assert(ourPendingUpdate == null)
    }

    @JvmStatic
    fun applyLastCompletionStatisticsUpdate() {
      ourPendingUpdate?.let {
        StatisticsManager.getInstance().incUseCount(it.myInfo)
        (FeatureUsageTracker.getInstance() as FeatureUsageTrackerImpl).completionStatistics.registerInvocation(it.mySpared)
      }
      cancelLastCompletionStatisticsUpdate()
    }
  }
}

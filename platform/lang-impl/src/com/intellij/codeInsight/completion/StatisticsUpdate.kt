/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion

import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.featureStatistics.FeatureUsageTrackerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.statistics.StatisticsInfo
import com.intellij.util.Alarm

/**
 * @author peter
 */
class StatisticsUpdate
    private constructor(private val myInfo: StatisticsInfo) : Disposable {
  private var mySpared: Int = 0

  override fun dispose() {}

  fun addSparedChars(indicator: CompletionProgressIndicator, item: LookupElement, context: InsertionContext) {
    val textInserted: String
    if (context.offsetMap.containsOffset(CompletionInitializationContext.START_OFFSET) &&
        context.offsetMap.containsOffset(InsertionContext.TAIL_OFFSET) &&
        context.tailOffset >= context.startOffset) {
      textInserted = context.document.immutableCharSequence.subSequence(context.startOffset, context.tailOffset).toString()
    }
    else {
      textInserted = item.lookupString
    }
    val withoutSpaces = StringUtil.replace(textInserted, arrayOf(" ", "\t", "\n"), arrayOf("", "", ""))
    var spared = withoutSpaces.length - indicator.lookup.itemPattern(item).length
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
    val listener = object : DocumentListener {
      override fun beforeDocumentChange(e: DocumentEvent) {
        if (!marker.isValid || e.offset > marker.startOffset && e.offset < marker.endOffset) {
          cancelLastCompletionStatisticsUpdate()
        }
      }
    }

    ourStatsAlarm.addRequest({
                               if (ourPendingUpdate === this) {
                                 applyLastCompletionStatisticsUpdate()
                               }
                             }, 20 * 1000)

    document.addDocumentListener(listener)
    Disposer.register(this, Disposable {
      document.removeDocumentListener(listener)
      marker.dispose()
      ourStatsAlarm.cancelAllRequests()
    })
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
        it.myInfo.incUseCount()
        (FeatureUsageTracker.getInstance() as FeatureUsageTrackerImpl).completionStatistics.registerInvocation(it.mySpared)
      }
      cancelLastCompletionStatisticsUpdate()
    }
  }
}

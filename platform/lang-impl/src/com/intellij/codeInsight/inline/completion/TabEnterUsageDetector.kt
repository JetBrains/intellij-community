// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.ide.util.PropertiesComponent

class TabEnterUsageDetector : LookupManagerListener {
  private val properties = PropertiesComponent.getInstance()

  private var tabCount
    get() = properties.getInt(TAB_SELECTION_COUNT_KEY, 0)
    set(value) = properties.setValue(TAB_SELECTION_COUNT_KEY, value.toString())

  private var enterCount
    get() = properties.getInt(ENTER_SELECTION_COUNT_KEY, 0)
    set(value) = properties.setValue(ENTER_SELECTION_COUNT_KEY, value.toString())

  private val totalCount
    get() = tabCount + enterCount

  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    if (detectionFinished()) return
    newLookup?.addLookupListener(object : LookupListener {
      override fun itemSelected(event: LookupEvent) {
        when (event.completionChar){
          '\t' -> tabCount++
          '\n' -> enterCount++
        }
      }
    })
  }

  fun inlineCompletionChar(): Char? {
    if (!detectionFinished()) return null
    val tabRatio = tabCount.toDouble() / totalCount
    return if (tabRatio > TAB_RATIO_THRESHOLD) '\t' else '\n'
  }

  fun detectionFinished() = totalCount >= SELECTION_COUNT_THRESHOLD

  companion object {
    const val TAB_SELECTION_COUNT_KEY = "tab_selection_count"
    const val ENTER_SELECTION_COUNT_KEY = "enter_selection_count"

    const val SELECTION_COUNT_THRESHOLD = 5
    const val TAB_RATIO_THRESHOLD = 0.1
  }
}
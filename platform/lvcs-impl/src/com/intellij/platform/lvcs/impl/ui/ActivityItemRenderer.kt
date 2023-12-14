// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.ui

import com.intellij.platform.lvcs.impl.ActivityItem
import com.intellij.platform.lvcs.impl.ActivityPresentation
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.text.DateFormatUtil
import javax.swing.JList

class ActivityItemRenderer(val presentationFunction: (item: ActivityItem) -> ActivityPresentation?) : ColoredListCellRenderer<ActivityItem>() {
  override fun customizeCellRenderer(list: JList<out ActivityItem>,
                                     value: ActivityItem,
                                     index: Int,
                                     selected: Boolean,
                                     hasFocus: Boolean) {
    val presentation = presentationFunction(value) ?: return
    append(presentation.text)
    if (presentation.text.isNotBlank()) append(", ", SimpleTextAttributes.GRAY_ATTRIBUTES)
    append(DateFormatUtil.formatPrettyDateTime(value.timestamp), SimpleTextAttributes.GRAY_ATTRIBUTES)
  }
}
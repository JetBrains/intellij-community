// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview

import com.intellij.util.ui.JBUI
import javax.swing.border.Border

object CodeReviewTimelineUIUtil {
  const val VERT_PADDING: Int = 6
  const val HEADER_VERT_PADDING: Int = 20

  const val ITEM_HOR_PADDING: Int = 16
  const val ITEM_VERT_PADDING: Int = 10

  val ITEM_BORDER: Border get() = JBUI.Borders.empty(ITEM_HOR_PADDING, ITEM_VERT_PADDING)

  object Thread {
    const val DIFF_TEXT_GAP = 8

    object Replies {
      object ActionsFolded {
        const val VERTICAL_PADDING = 8
        const val HORIZONTAL_GAP = 8
        const val HORIZONTAL_GROUP_GAP = 14
      }
    }
  }
}
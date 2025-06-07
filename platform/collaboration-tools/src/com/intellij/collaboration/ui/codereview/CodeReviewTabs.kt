// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview

import com.intellij.collaboration.messages.CollaborationToolsBundle.message
import com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.content.AlertIcon
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.TabLabel
import icons.CollaborationToolsIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

@Internal
object CodeReviewTabs {
  fun CoroutineScope.bindTabText(tab: TabInfo, text: Supplier<@Nls String>, countFlow: Flow<Int?>): Job {
    return countFlow
      .onEach { count ->
        tab.setText(text)
        tab.appendCount(count)
      }
      .launchIn(this)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Deprecated with the move to the new design")
  fun CoroutineScope.bindTabUi(
    tabs: JBTabsImpl,
    tab: TabInfo,
    text: Supplier<@Nls String>,
    totalFlow: Flow<Int?>,
    unreadFlow: Flow<Int?>
  ): Job {
    return combine(totalFlow, unreadFlow, ::Pair)
      .onEach { (total, unread) ->
        tab.setText(text)
        tab.appendUnreadIcon(tabs.getTabLabel(tab)!!, unread)
        tab.appendCount(total, unread == null || unread <= 0)

        tab.setUnreadTooltip(unread)
      }
      .launchIn(this)
  }
}

private fun TabInfo.setText(text: Supplier<@Nls String>) {
  clearText(false).append(text.get(), REGULAR_ATTRIBUTES)
}

private fun TabInfo.appendCount(count: Int?, smallGap: Boolean = true) {
  count?.let { append(if (smallGap) "  $it" else "   $it", GRAYED_ATTRIBUTES) }
}

private fun TabInfo.appendUnreadIcon(tabLabel: TabLabel, unread: Int?) {
  if (unread == null || unread <= 0) {
    stopAlerting()
  }
  else {
    setAlertIcon(AlertIcon(
      CollaborationToolsIcons.Review.FileUnread,
      0,
      tabLabel.labelComponent.preferredSize.width + scale(3)
    ))
    fireAlert()
    resetAlertRequest()
  }
}

private fun TabInfo.setUnreadTooltip(unread: Int?) {
  setTooltipText(if (unread != null && unread > 0) message("tooltip.code.review.files.not.viewed", unread) else null)
}